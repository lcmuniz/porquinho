package com.porquinho.service;

import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.TransferResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deploy checklist 4.1, option (a): a transfer registered after the import of the same TED links the imported
 * transaction already in the balance (decision D4 rule) instead of counting the amount again. PostgreSQL via
 * Testcontainers. The imported rows are inserted already finished: CONFIRMED, in the balance, with the balance moved.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferImportedLinkIntegrationTest {

    private static final String IP = "127.0.0.1";

    @Autowired
    private TransferService transferService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID nubank;
    private UUID inter;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        nubank = newAccount("1000.00");
        inter = newAccount("0.00");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transfers WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private UUID newAccount(String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Conta', 'CHECKING', ?, ?)", id, userId, new BigDecimal(balance), new BigDecimal(balance));
        return id;
    }

    /** A finished imported row in the balance; the account balance moves by it, as the import conclusion does. */
    private UUID imported(UUID accountId, String date, String type, String amount) {
        UUID importId = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, status) "
            + "VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, 'COMPLETED')", importId, userId, accountId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, import_id, external_id, transaction_date, "
            + "description, original_description, amount, transaction_type, review_status, affects_balance) "
            + "VALUES (?, ?, ?, ?, ?, ?::date, 'TED', 'TED', ?, ?, 'CONFIRMED', true)",
            id, userId, accountId, importId, "fit-" + id, date, new BigDecimal(amount), type);
        BigDecimal signed = "DEBIT".equals(type) ? new BigDecimal(amount).negate() : new BigDecimal(amount);
        jdbc.update("UPDATE accounts SET current_balance = current_balance + ? WHERE id = ?", signed, accountId);
        return id;
    }

    private TransferResponse transfer(UUID id, UUID from, UUID to, String date, String amount) {
        return transferService.create(userId, new CreateTransferRequest(id, from, to, new BigDecimal(amount),
            LocalDate.parse(date), "TED"), IP).transfer();
    }

    private TransferResponse transfer(String date, String amount) {
        return transfer(UUID.randomUUID(), nubank, inter, date, amount);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT affects_balance, absorbed_by_transfer_id, deleted_at FROM transactions WHERE id = ?", id);
    }

    private void assertLinked(UUID rowId, UUID transferId) {
        assertThat(row(rowId)).containsEntry("affects_balance", false).containsEntry("absorbed_by_transfer_id", transferId);
    }

    private void assertNotLinked(UUID rowId) {
        assertThat(row(rowId)).containsEntry("affects_balance", true).containsEntry("absorbed_by_transfer_id", null);
    }

    @Test
    void theImportedDebitOfTheOriginIsLinkedAndNotCountedAgain() {
        UUID ted = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        assertThat(balance(nubank)).isEqualByComparingTo("900.00");

        TransferResponse response = transfer("2026-11-05", "100.00");

        assertThat(balance(nubank)).isEqualByComparingTo("900.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertLinked(ted, response.id());
        assertThat(response.linkedImportedTransactions()).isEqualTo(1);
        // the "transfer" snapshot holds the balance after the link
        assertThat(jdbc.queryForObject("SELECT balance_snapshot FROM account_balance_history WHERE account_id = ? "
            + "AND created_by_event = 'transfer'", BigDecimal.class, nubank)).isEqualByComparingTo("900.00");
    }

    @Test
    void theImportedCreditOfTheDestinationIsLinkedAndNotCountedAgain() {
        UUID ted = imported(inter, "2026-11-04", "CREDIT", "100.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");

        TransferResponse response = transfer("2026-11-04", "100.00");

        assertThat(balance(nubank)).isEqualByComparingTo("900.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertLinked(ted, response.id());
        assertThat(response.linkedImportedTransactions()).isEqualTo(1);
    }

    @Test
    void bothSidesImportedAreBothLinked() {
        UUID out = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        UUID in = imported(inter, "2026-11-05", "CREDIT", "100.00");

        TransferResponse response = transfer("2026-11-04", "100.00");

        assertThat(balance(nubank)).isEqualByComparingTo("900.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertLinked(out, response.id());
        assertLinked(in, response.id());
        assertThat(response.linkedImportedTransactions()).isEqualTo(2);
    }

    @Test
    void withoutAMatchingImportedRowTheTransferMovesTheBalanceAsBefore() {
        UUID otherAmount = imported(nubank, "2026-11-04", "DEBIT", "99.99");
        UUID wrongDirection = imported(nubank, "2026-11-04", "CREDIT", "100.00");
        UUID tooFar = imported(nubank, "2026-11-02", "DEBIT", "100.00");
        UUID removed = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        UUID outOfBalance = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        UUID manual = UUID.randomUUID();
        jdbc.update("UPDATE transactions SET deleted_at = NOW(), affects_balance = false WHERE id = ?", removed);
        jdbc.update("UPDATE transactions SET affects_balance = false WHERE id = ?", outOfBalance);
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, transaction_date, description, original_description, amount, "
            + "transaction_type, review_status, affects_balance) VALUES (?, ?, ?, '2026-11-04', 'TED', 'TED', 100.00, 'DEBIT', "
            + "'CONFIRMED', true)", manual, userId, nubank);
        BigDecimal before = balance(nubank);

        TransferResponse response = transfer("2026-11-04", "100.00");

        assertThat(balance(nubank)).isEqualByComparingTo(before.subtract(new BigDecimal("100.00")));
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertThat(response.linkedImportedTransactions()).isZero();
        Stream.of(otherAmount, wrongDirection, tooFar).forEach(this::assertNotLinked);
        assertThat(row(manual)).containsEntry("absorbed_by_transfer_id", null);
        assertThat(row(removed)).containsEntry("absorbed_by_transfer_id", null);
        assertThat(row(outOfBalance)).containsEntry("absorbed_by_transfer_id", null);
    }

    @Test
    void withTwoCandidatesTheClosestDateWinsThenTheLowestId() {
        UUID dayBefore = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        UUID sameDayA = imported(nubank, "2026-11-05", "DEBIT", "100.00");
        UUID sameDayB = imported(nubank, "2026-11-05", "DEBIT", "100.00");
        UUID first = sameDayA.compareTo(sameDayB) < 0 ? sameDayA : sameDayB;
        UUID second = first == sameDayA ? sameDayB : sameDayA;
        assertThat(balance(nubank)).isEqualByComparingTo("700.00");

        TransferResponse one = transfer("2026-11-05", "100.00");
        assertLinked(first, one.id());
        assertNotLinked(second);
        assertNotLinked(dayBefore);

        // the next transfer of the same TED does not take a row already linked
        TransferResponse two = transfer("2026-11-05", "100.00");
        assertLinked(second, two.id());
        assertThat(balance(nubank)).isEqualByComparingTo("700.00");
        assertThat(balance(inter)).isEqualByComparingTo("200.00");
    }

    @Test
    void repeatingTheTransferIdLinksNothingMore() {
        UUID ted = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        UUID other = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        UUID id = UUID.randomUUID();

        transfer(id, nubank, inter, "2026-11-04", "100.00");
        TransferResponse again = transfer(id, nubank, inter, "2026-11-04", "100.00");

        assertThat(again.linkedImportedTransactions()).isZero();
        assertThat(balance(nubank)).isEqualByComparingTo("800.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertThat(Stream.of(ted, other).filter(r -> row(r).get("absorbed_by_transfer_id") != null)).hasSize(1);
    }

    @Test
    void removingTheLinkedImportedTransactionKeepsTheTransferInTheBalance() {
        // there is no deletion or edition of transfers yet (V22); the user can only remove the imported row
        UUID ted = imported(nubank, "2026-11-04", "DEBIT", "100.00");
        transfer("2026-11-04", "100.00");

        transactionService.delete(userId, ted, IP);

        assertThat(balance(nubank)).isEqualByComparingTo("900.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertThat(row(ted).get("deleted_at")).isNotNull();
    }
}
