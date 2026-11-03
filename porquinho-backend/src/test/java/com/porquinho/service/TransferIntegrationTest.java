package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.TransferResponse;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.TransferRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 4.5: transfers against the real schema (PostgreSQL via Testcontainers, Flyway V22): both balances, the two
 * "transfer" snapshots, the transfer row and the audit in one transaction; 404 for another user's or a removed
 * account with nothing changed; atomicity; crossed concurrent transfers without deadlock; the list with the account
 * names; the CHECKs; purge order and export.
 * Not @Transactional: each service call commits on its own thread, so the concurrent requests really race.
 * Cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transfers WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID add(UUID owner, String name, String balance) {
        return accountService.create(owner, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal(balance))).getId();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private TransferResponse transfer(UUID from, UUID to, String amount) {
        return transferService.create(userId,
            new CreateTransferRequest(UUID.randomUUID(), from, to, new BigDecimal(amount), today(), null), "127.0.0.1")
            .transfer();
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void nubankToInterMovesTheMoneyAndRecordsEverything() {
        // Acceptance criterion: Nubank R$ 1.234,56, Inter R$ 600,00, transfer R$ 200,00 from Nubank to Inter
        UUID nubank = add(userId, "Nubank", "1234.56");
        UUID inter = add(userId, "Inter", "600.00");

        TransferResponse response = transferService.create(userId,
            new CreateTransferRequest(UUID.randomUUID(), nubank, inter, new BigDecimal("200.00"), today(), " Reserva "),
            "10.0.0.1").transfer();

        assertThat(balance(nubank)).isEqualByComparingTo("1034.56");
        assertThat(balance(inter)).isEqualByComparingTo("800.00");
        assertThat(balance(nubank).add(balance(inter))).isEqualByComparingTo("1834.56");  // total unchanged

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT user_id, from_account_id, to_account_id, amount, transfer_date, description, deleted_at FROM transfers WHERE id = ?",
            response.id());
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("from_account_id")).isEqualTo(nubank);
        assertThat(row.get("to_account_id")).isEqualTo(inter);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("200.00");
        assertThat(row.get("transfer_date").toString()).isEqualTo(today().toString());
        assertThat(row.get("description")).isEqualTo("Reserva");
        assertThat(row.get("deleted_at")).isNull();

        assertThat(jdbc.queryForObject("SELECT balance_snapshot FROM account_balance_history "
            + "WHERE account_id = ? AND created_by_event = 'transfer'", BigDecimal.class, nubank)).isEqualByComparingTo("1034.56");
        assertThat(jdbc.queryForObject("SELECT balance_snapshot FROM account_balance_history "
            + "WHERE account_id = ? AND created_by_event = 'transfer'", BigDecimal.class, inter)).isEqualByComparingTo("800.00");
        // V22: both snapshots point at the transfer
        assertThat(jdbc.queryForList("SELECT transfer_id FROM account_balance_history WHERE user_id = ? "
            + "AND created_by_event = 'transfer'", UUID.class, userId)).containsExactly(response.id(), response.id());
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE user_id = ? AND created_by_event = 'initial' "
            + "AND transfer_id IS NOT NULL", userId)).isZero();

        Map<String, Object> audit = jdbc.queryForMap(
            "SELECT ip_address, metadata FROM audit_logs WHERE user_id = ? AND event_type = 'transfer_created'", userId);
        assertThat(audit.get("ip_address")).isEqualTo("10.0.0.1");
        assertThat(audit.get("metadata").toString()).contains(response.id().toString()).contains("200.00");

        assertThat(response.fromAccountName()).isEqualTo("Nubank");
        assertThat(response.toAccountName()).isEqualTo("Inter");
    }

    @Test
    void negativeResultingBalanceIsAllowed() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "0");

        transfer(nubank, inter, "150.00");

        assertThat(balance(nubank)).isEqualByComparingTo("-50.00");
        assertThat(balance(inter)).isEqualByComparingTo("150.00");
    }

    @Test
    void anotherUsersAccountIs404AndNoBalanceChanges() {
        UUID mine = add(userId, "Nubank", "100.00");
        UUID theirs = add(newUser(), "Alheia", "100.00");

        assertThatThrownBy(() -> transfer(mine, theirs, "10"))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Conta não encontrada");
        assertThatThrownBy(() -> transfer(theirs, mine, "10"))
            .isInstanceOf(ResourceNotFoundException.class);

        assertThat(balance(mine)).isEqualByComparingTo("100.00");
        assertThat(balance(theirs)).isEqualByComparingTo("100.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE created_by_event = 'transfer' "
            + "AND account_id IN (?, ?)", mine, theirs)).isZero();
    }

    @Test
    void removedAccountIs404AndNoBalanceChanges() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        accountService.delete(userId, inter, "127.0.0.1");

        assertThatThrownBy(() -> transfer(nubank, inter, "10")).isInstanceOf(ResourceNotFoundException.class);

        assertThat(balance(nubank)).isEqualByComparingTo("100.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void failureWhileWritingTheTransferRollsBackBothBalancesAndTheHistory() {
        // The service does not check the description length (the request validation does); a 201-character
        // description sent straight to it passes the locks and the balance checks, then the VARCHAR(200) fails in the
        // database: nothing may remain.
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");

        assertThatThrownBy(() -> transferService.create(userId, new CreateTransferRequest(UUID.randomUUID(), nubank,
            inter, new BigDecimal("10.00"), today(), "a".repeat(201)), "127.0.0.1"))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balance(nubank)).isEqualByComparingTo("100.00");
        assertThat(balance(inter)).isEqualByComparingTo("100.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE user_id = ? AND created_by_event = 'transfer'",
            userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'transfer_created'", userId))
            .isZero();
    }

    @Test
    void sameIdPostedTwiceCreatesOneTransferAndMovesTheBalancesOnce() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        CreateTransferRequest request =
            new CreateTransferRequest(UUID.randomUUID(), nubank, inter, new BigDecimal("30.00"), today(), null);

        TransferService.Result first = transferService.create(userId, request, "127.0.0.1");
        TransferService.Result second = transferService.create(userId, request, "127.0.0.1");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.transfer().id()).isEqualTo(request.id());
        assertThat(second.transfer().fromAccountName()).isEqualTo("Nubank");
        assertThat(balance(nubank)).isEqualByComparingTo("70.00");
        assertThat(balance(inter)).isEqualByComparingTo("130.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE user_id = ? AND created_by_event = 'transfer'",
            userId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'transfer_created'", userId))
            .isEqualTo(1);
    }

    @Test
    void sameIdPostedConcurrentlyCreatesOneTransfer() throws Exception {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        CreateTransferRequest request =
            new CreateTransferRequest(UUID.randomUUID(), nubank, inter, new BigDecimal("30.00"), today(), null);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<TransferService.Result>> results = List.of(
                pool.submit(() -> {
                    start.await();
                    return transferService.create(userId, request, "127.0.0.1");
                }),
                pool.submit(() -> {
                    start.await();
                    return transferService.create(userId, request, "127.0.0.1");
                }));
            start.countDown();
            List<Boolean> created = new ArrayList<>();
            for (Future<TransferService.Result> result : results) {
                TransferService.Result r = result.get(30, TimeUnit.SECONDS);
                assertThat(r.transfer().id()).isEqualTo(request.id());
                created.add(r.created());
            }
            assertThat(created).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(balance(nubank)).isEqualByComparingTo("70.00");
        assertThat(balance(inter)).isEqualByComparingTo("130.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isEqualTo(1);
    }

    @Test
    void idOfAnotherUsersTransferIs404AndNothingChanges() {
        UUID other = newUser();
        UUID o1 = add(other, "O1", "100.00");
        UUID o2 = add(other, "O2", "100.00");
        UUID transferId = UUID.randomUUID();
        transferService.create(other, new CreateTransferRequest(transferId, o1, o2, BigDecimal.ONE, today(), null),
            "127.0.0.1");
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");

        assertThatThrownBy(() -> transferService.create(userId,
            new CreateTransferRequest(transferId, nubank, inter, BigDecimal.TEN, today(), null), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage(TransferService.NOT_FOUND_MESSAGE);
        assertThat(balance(nubank)).isEqualByComparingTo("100.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void listReturnsAtMostThe20MostRecent() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        UUID inter = add(userId, "Inter", "1000.00");
        for (int day = 0; day < 21; day++) {
            transferService.create(userId, new CreateTransferRequest(UUID.randomUUID(), nubank, inter,
                new BigDecimal(day + 1), today().minusDays(day), null), "127.0.0.1");
        }

        List<TransferResponse> list = transferService.listRecent(userId);

        assertThat(list).hasSize(20);
        assertThat(list.get(0).transferDate()).isEqualTo(today());
        assertThat(list.get(19).transferDate()).isEqualTo(today().minusDays(19));
    }

    @Test
    void crossedConcurrentTransfersDoNotDeadlock() throws Exception {
        UUID a = add(userId, "A", "1000.00");
        UUID b = add(userId, "B", "1000.00");
        int rounds = 5;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < rounds; i++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<TransferResponse>> results = List.of(
                    pool.submit(() -> {
                        start.await();
                        return transfer(a, b, "10.00");
                    }),
                    pool.submit(() -> {
                        start.await();
                        return transfer(b, a, "30.00");
                    }));
                start.countDown();
                for (Future<TransferResponse> result : results) {
                    assertThat(result.get(30, TimeUnit.SECONDS)).isNotNull();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(balance(a)).isEqualByComparingTo("1100.00");
        assertThat(balance(b)).isEqualByComparingTo("900.00");
        assertThat(count("SELECT COUNT(*) FROM transfers WHERE user_id = ?", userId)).isEqualTo(2L * rounds);
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE user_id = ? AND created_by_event = 'transfer'",
            userId)).isEqualTo(4L * rounds);
    }

    @Test
    void listIsMostRecentFirstWithTheAccountNamesIncludingRemovedAccounts() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        add(userId, "BB", "100.00");  // keeps an active account after removing Inter
        transferService.create(userId,
            new CreateTransferRequest(UUID.randomUUID(), nubank, inter, new BigDecimal("10"), today().minusDays(2), "Antiga"), "127.0.0.1");
        transferService.create(userId,
            new CreateTransferRequest(UUID.randomUUID(), inter, nubank, new BigDecimal("20"), today(), null), "127.0.0.1");
        transferService.create(userId,
            new CreateTransferRequest(UUID.randomUUID(), nubank, inter, new BigDecimal("30"), today().minusDays(2), "Mesma data, depois"),
            "127.0.0.1");
        accountService.delete(userId, inter, "127.0.0.1");
        UUID other = newUser();
        UUID o1 = add(other, "O1", "1");
        UUID o2 = add(other, "O2", "1");
        transferService.create(other, new CreateTransferRequest(UUID.randomUUID(), o1, o2, BigDecimal.ONE, today(), null), "127.0.0.1");

        List<TransferResponse> list = transferService.listRecent(userId);

        assertThat(list).extracting(TransferResponse::amount)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("10"));
        assertThat(list.get(0).fromAccountName()).isEqualTo("Inter");
        assertThat(list.get(0).toAccountName()).isEqualTo("Nubank");
        assertThat(list.get(2).description()).isEqualTo("Antiga");
    }

    @Test
    void checksRejectTheSameAccountAndNonPositiveAmounts() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        String insert = "INSERT INTO transfers (user_id, from_account_id, to_account_id, amount, transfer_date) "
            + "VALUES (?, ?, ?, ?, CURRENT_DATE)";

        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, nubank, new BigDecimal("1")))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, userId, nubank, inter, BigDecimal.ZERO))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void purgeOrderDeletesTransfersBeforeAccountsAndTheUser() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        transfer(nubank, inter, "10");

        // The FK from transfers blocks deleting the accounts first
        assertThatThrownBy(() -> jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId))
            .isInstanceOf(DataIntegrityViolationException.class);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(transferRepository.deleteByUserId(userId)).isEqualTo(1);
            // V22: ON DELETE SET NULL clears the link of the two "transfer" snapshots, so they do not block
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_balance_history WHERE user_id = ? "
                + "AND transfer_id IS NOT NULL", Long.class, userId)).isZero();
            assertThat(balanceHistoryRepository.deleteByUserId(userId)).isEqualTo(4);
            assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(2);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
            assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        });
    }

    @Test
    void exportIncludesTheTransfers() {
        UUID nubank = add(userId, "Nubank", "100.00");
        UUID inter = add(userId, "Inter", "100.00");
        TransferResponse created = transfer(nubank, inter, "10");

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("transfers");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id")).isEqualTo(created.id().toString());
        assertThat(rows.get(0).get("from_account_id")).isEqualTo(nubank.toString());
        assertThat(rows.get(0).get("to_account_id")).isEqualTo(inter.toString());

        @SuppressWarnings("unchecked")
        var history = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("account_balance_history");
        assertThat(history).filteredOn(h -> "transfer".equals(h.get("created_by_event")))
            .extracting(h -> h.get("transfer_id")).containsOnly(created.id().toString());
    }
}
