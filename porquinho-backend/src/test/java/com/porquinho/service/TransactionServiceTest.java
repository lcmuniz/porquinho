package com.porquinho.service;

import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.TransactionCreateRequest;
import com.porquinho.dto.TransactionResponse;
import com.porquinho.dto.TransactionUpdateRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.TransactionImportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stories 5.12 and 5.13: listing, adding, editing and removing confirmed transactions against the real schema (PostgreSQL via
 * Testcontainers, Flyway through V31). Every account has its D3 reference on 2026-11-01 (São Paulo).
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionServiceTest {

    private static final String IP = "127.0.0.1";

    @Autowired
    private TransactionService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionImportRepository transactionImportRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    /** Never the real client: nothing here goes to the AI. */
    @MockitoBean
    private TransactionClassifier classifier;

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
            jdbc.update("DELETE FROM ai_classification_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM user_classification_patterns WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID newAccount(UUID owner, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Nubank', 'CHECKING', ?, ?)", id, owner, new BigDecimal(balance), new BigDecimal(balance));
        jdbc.update("INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, "
            + "created_by_event) VALUES (?, ?, ?, '2026-11-01T15:00:00Z'::timestamptz, 'initial')", id, owner,
            new BigDecimal(balance));
        return id;
    }

    private UUID newAccount(String balance) {
        return newAccount(userId, balance);
    }

    private UUID newCard() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO credit_cards (id, user_id, card_name, card_limit, due_day) "
            + "VALUES (?, ?, 'Nubank', 1000, 10)", id, userId);
        return id;
    }

    private UUID newCategory(String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }

    /** A confirmed account row, already in the balance when {@code affects}. */
    private UUID newRow(UUID owner, UUID accountId, String date, String type, String amount, boolean affects) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, transaction_date, description, "
            + "original_description, amount, transaction_type, review_status, affects_balance) "
            + "VALUES (?, ?, ?, ?::date, 'MERCADO', 'MERCADO', ?, ?, 'CONFIRMED', ?)", id, owner, accountId, date,
            new BigDecimal(amount), type, affects);
        return id;
    }

    private UUID newRow(UUID accountId, String date, String type, String amount, boolean affects) {
        return newRow(userId, accountId, date, type, amount, affects);
    }

    private UUID newCardRow(UUID cardId, String date, Integer number, Integer total, UUID group, boolean future) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, credit_card_id, transaction_date, description, "
            + "original_description, amount, transaction_type, review_status, is_installment, installment_number, "
            + "installment_total, installment_group_id, is_future_installment) VALUES (?, ?, ?, ?::date, 'LOJA', "
            + "'LOJA', 100, 'DEBIT', 'CONFIRMED', ?, ?, ?, ?, ?)", id, userId, cardId, date, number != null, number,
            total, group, future);
        return id;
    }

    private TransactionUpdateRequest edit(String date, String type, String amount, UUID categoryId, UUID accountId,
                                          UUID cardId) {
        return new TransactionUpdateRequest(LocalDate.parse(date), "MERCADO", new BigDecimal(amount), type, categoryId,
            accountId, cardId);
    }

    private Map<String, Object> rowOf(UUID id) {
        return jdbc.queryForMap("SELECT * FROM transactions WHERE id = ?", id);
    }

    private BigDecimal balanceOf(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private int transactionSnapshots(UUID accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_balance_history WHERE account_id = ? "
            + "AND created_by_event = 'transaction'", Integer.class, accountId);
    }

    private List<String> auditOf(String event) {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = ?", String.class,
            userId, event);
    }

    @Test
    void newAmountOnTheSameAccountAppliesTheDifference() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);

        TransactionResponse response = service.update(userId, row, edit("2026-11-05", "DEBIT", "150.00", null, account,
            null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("950.00");
        assertThat(transactionSnapshots(account)).isEqualTo(1);
        assertThat(rowOf(row).get("affects_balance")).isEqualTo(true);
        assertThat(response.amount()).isEqualByComparingTo("150.00");
        assertThat(response.accountId()).isEqualTo(account);
        assertThat(auditOf(TransactionService.AUDIT_EDITED)).singleElement().asString().contains(row.toString());
    }

    @Test
    void movingToAnotherAccountMovesTheEffect() {
        UUID a = newAccount("1000.00");
        UUID b = newAccount("500.00");
        UUID row = newRow(a, "2026-11-05", "DEBIT", "100.00", true);

        service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", null, b, null), IP);

        assertThat(balanceOf(a)).isEqualByComparingTo("1100.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("400.00");
        assertThat(transactionSnapshots(a)).isEqualTo(1);
        assertThat(transactionSnapshots(b)).isEqualTo(1);
        assertThat(rowOf(row).get("account_id")).isEqualTo(b);
    }

    @Test
    void inactiveAccountIs404WithNothingWritten() {
        UUID a = newAccount("1000.00");
        UUID b = newAccount("500.00");
        jdbc.update("UPDATE accounts SET deleted_at = now() WHERE id = ?", b);
        UUID row = newRow(a, "2026-11-05", "DEBIT", "100.00", true);

        assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", null, b, null), IP))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");
        assertThat(rowOf(row).get("account_id")).isEqualTo(a);
    }

    @Test
    void dateOnOrBeforeTheReferenceTakesTheRowOutOfTheBalance() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);

        service.update(userId, row, edit("2026-11-01", "DEBIT", "100.00", null, account, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("1100.00");
        assertThat(rowOf(row).get("affects_balance")).isEqualTo(false);
    }

    @Test
    void rowReconciledAfterwardsLeavesTheBalanceAlone() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);
        jdbc.update("UPDATE accounts SET last_reconciliation_date = '2026-11-06T15:00:00Z'::timestamptz WHERE id = ?",
            account);

        service.update(userId, row, edit("2026-11-05", "DEBIT", "300.00", null, account, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
        assertThat(transactionSnapshots(account)).isZero();
    }

    /** Retro Epic 5, X2: a future row already in the balance is absorbed when the user sets the balance by hand. */
    @Test
    void reconciliationAbsorbsFutureRowsSoRemovingOrEditingThemKeepsTheBankBalance() {
        UUID account = newAccount("900.00");
        UUID removed = newRow(account, "2027-06-01", "DEBIT", "100.00", true);
        UUID edited = newRow(account, "2027-06-02", "DEBIT", "50.00", true);

        reconciliationService.reconcile(userId, account, new CreateReconciliationRequest(new BigDecimal("700.00"), null),
            IP);
        assertThat(rowOf(removed).get("affects_balance")).isEqualTo(false);

        service.delete(userId, removed, IP);
        service.update(userId, edited, edit("2027-06-03", "DEBIT", "80.00", null, account, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("700.00");
        assertThat(rowOf(edited).get("affects_balance")).isEqualTo(false);
        assertThat(transactionSnapshots(account)).isZero();
    }

    @Test
    void balanceEditAbsorbsFutureRowsButAnotherAccountStillGetsTheEffect() {
        UUID account = newAccount("900.00");
        UUID other = newAccount("500.00");
        UUID row = newRow(account, "2027-06-01", "DEBIT", "100.00", true);
        Long version = jdbc.queryForObject("SELECT version FROM accounts WHERE id = ?", Long.class, account);

        accountService.update(userId, account, new UpdateAccountRequest("Nubank", null, "CHECKING",
            new BigDecimal("1000.00"), version));
        service.update(userId, row, edit("2027-06-01", "DEBIT", "100.00", null, other, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(other)).isEqualByComparingTo("400.00");
        assertThat(rowOf(row).get("affects_balance")).isEqualTo(true);
    }

    @Test
    void movingToACardTakesTheEffectOut() {
        UUID account = newAccount("1000.00");
        UUID card = newCard();
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);

        service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", null, null, card), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("1100.00");
        Map<String, Object> saved = rowOf(row);
        assertThat(saved.get("affects_balance")).isEqualTo(false);
        assertThat(saved.get("credit_card_id")).isEqualTo(card);
        assertThat(saved.get("account_id")).isNull();
    }

    @Test
    void removingACreditTakesItOutOfTheBalanceAndASecondTimeIs404() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "CREDIT", "200.00", true);

        service.delete(userId, row, IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("800.00");
        assertThat(rowOf(row).get("deleted_at")).isNotNull();
        assertThat(auditOf(TransactionService.AUDIT_DELETED)).hasSize(1);
        assertThat(service.list(userId, "2026-11")).isEmpty();
        assertThatThrownBy(() -> service.delete(userId, row, IP)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(balanceOf(account)).isEqualByComparingTo("800.00");
    }

    @Test
    void balanceOutOfRangeIs400WithNothingWritten() {
        UUID account = newAccount("-9999999999999.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "0.50", true);

        assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "10.00", null, account, null),
            IP)).isInstanceOf(BadRequestException.class);
        assertThat(balanceOf(account)).isEqualByComparingTo("-9999999999999.00");
        assertThat(rowOf(row).get("amount")).isEqualTo(new BigDecimal("0.50"));
    }

    @Test
    void rowsInReviewFutureRemovedOrOfAnotherUserAre404() {
        UUID account = newAccount("1000.00");
        UUID pending = newRow(account, "2026-11-05", "DEBIT", "100.00", false);
        jdbc.update("UPDATE transactions SET review_status = 'PENDING' WHERE id = ?", pending);
        UUID removed = newRow(account, "2026-11-05", "DEBIT", "100.00", false);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = ?", removed);
        UUID other = newUser();
        UUID otherRow = newRow(other, newAccount(other, "10.00"), "2026-11-05", "DEBIT", "1.00", false);
        UUID future = newCardRow(newCard(), "2026-12-05", 4, 10, UUID.randomUUID(), true);

        for (UUID id : List.of(pending, removed, otherRow, future)) {
            assertThatThrownBy(() -> service.update(userId, id, edit("2026-11-05", "DEBIT", "1.00", null, account, null),
                IP)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.delete(userId, id, IP)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.removeFutureInstallments(userId, id, IP))
                .isInstanceOf(ResourceNotFoundException.class);
        }
        assertThat(rowOf(pending).get("review_status")).isEqualTo("PENDING");
        assertThat(rowOf(otherRow).get("deleted_at")).isNull();
        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
    }

    @Test
    void newCategoryIsTheUsersChoiceAndIsLearned() {
        UUID account = newAccount("1000.00");
        UUID group = newCategory("Alimentação", null);
        UUID child = newCategory("Mercado", group);
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);

        TransactionResponse response = service.update(userId, row, new TransactionUpdateRequest(
            LocalDate.parse("2026-11-05"), "SUPERMERCADO EXTRA", new BigDecimal("100.00"), "DEBIT", child, account,
            null), IP);

        Map<String, Object> saved = rowOf(row);
        assertThat(saved.get("category_id")).isEqualTo(child);
        assertThat(saved.get("classification_source")).isEqualTo("USER");
        assertThat(saved.get("classification_confidence")).isEqualTo(100);
        assertThat(saved.get("original_description")).isEqualTo("MERCADO");
        assertThat(saved.get("description")).isEqualTo("SUPERMERCADO EXTRA");
        assertThat(response.category().name()).isEqualTo("Mercado");
        // decision Q5: the bank's text (the description before the edit) is what matches the next statement
        assertThat(jdbc.queryForList("SELECT description_pattern FROM user_classification_patterns WHERE user_id = ? "
            + "AND preferred_category_id = ?", String.class, userId, child)).containsExactly("mercado");
    }

    @Test
    void groupAsCategoryIs400AndAnEmptyCategoryTeachesNothing() {
        UUID account = newAccount("1000.00");
        UUID group = newCategory("Alimentação", null);
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);
        jdbc.update("UPDATE transactions SET category_id = ? WHERE id = ?", newCategory("Mercado", group), row);

        assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", group, account, null),
            IP)).isInstanceOf(BadRequestException.class);

        service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", null, account, null), IP);
        Map<String, Object> saved = rowOf(row);
        assertThat(saved.get("category_id")).isNull();
        assertThat(saved.get("classification_source")).isEqualTo("NONE");
        assertThat(saved.get("classification_confidence")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_classification_patterns WHERE user_id = ?",
            Integer.class, userId)).isZero();
    }

    @Test
    void sameRemovedCategoryIsKeptAsItIsAndTeachesNothing() {
        UUID account = newAccount("1000.00");
        UUID child = newCategory("Mercado", newCategory("Alimentação", null));
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);
        jdbc.update("UPDATE transactions SET category_id = ?, classification_source = 'AI', "
            + "classification_confidence = 85 WHERE id = ?", child, row);
        jdbc.update("UPDATE categories SET deleted_at = now() WHERE id = ?", child);

        service.update(userId, row, edit("2026-11-05", "DEBIT", "120.00", child, account, null), IP);

        Map<String, Object> saved = rowOf(row);
        assertThat(saved.get("amount")).isEqualTo(new BigDecimal("120.00"));
        assertThat(saved.get("category_id")).isEqualTo(child);
        assertThat(saved.get("classification_source")).isEqualTo("AI");
        assertThat(saved.get("classification_confidence")).isEqualTo(85);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_classification_patterns WHERE user_id = ?",
            Integer.class, userId)).isZero();
    }

    @Test
    void removingTheFutureInstallmentsKeepsThePurchaseAndMarksTheGroup() {
        UUID card = newCard();
        UUID group = UUID.randomUUID();
        UUID third = newCardRow(card, "2026-11-02", 3, 10, group, false);
        for (int n = 4; n <= 10; n++) {
            newCardRow(card, LocalDate.parse("2026-11-02").plusMonths(n - 3).toString(), n, 10, group, true);
        }
        assertThat(service.list(userId, "2026-11")).singleElement()
            .satisfies(r -> assertThat(r.remainingInstallments()).isEqualTo(7));

        assertThat(service.removeFutureInstallments(userId, third, IP)).isEqualTo(7);

        assertThat(rowOf(third).get("deleted_at")).isNull();
        assertThat(rowOf(third).get("installment_futures_removed_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE installment_group_id = ? "
            + "AND deleted_at IS NULL", Integer.class, group)).isEqualTo(1);
        assertThat(service.list(userId, "2026-11")).singleElement()
            .satisfies(r -> assertThat(r.remainingInstallments()).isZero());
        assertThat(auditOf(TransactionService.AUDIT_FUTURES_REMOVED)).singleElement().asString().contains("\"removed_installments\":7");
        assertThat(service.removeFutureInstallments(userId, third, IP)).isZero();
    }

    @Test
    void listShowsOneMonthNewestFirstWithoutPendingOrFutureRows() {
        UUID account = newAccount("1000.00");
        UUID older = newRow(account, "2026-11-03", "DEBIT", "1.00", true);
        UUID newer = newRow(account, "2026-11-20", "DEBIT", "1.00", true);
        newRow(account, "2026-10-31", "DEBIT", "1.00", true);
        UUID pending = newRow(account, "2026-11-04", "DEBIT", "1.00", false);
        jdbc.update("UPDATE transactions SET review_status = 'APPROVED' WHERE id = ?", pending);
        newCardRow(newCard(), "2026-11-10", 2, 3, UUID.randomUUID(), true);

        assertThat(service.list(userId, "2026-11")).extracting(TransactionResponse::id).containsExactly(newer, older);
        assertThatThrownBy(() -> service.list(userId, "11/2026")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void debitBecomingCreditSwapsTheSign() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);

        service.update(userId, row, edit("2026-11-05", "CREDIT", "100.00", null, account, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("1200.00");
        assertThat(rowOf(row).get("transaction_type")).isEqualTo("CREDIT");
    }

    @Test
    void rowOutOfTheBalanceEntersItByDateOrFromACard() {
        UUID account = newAccount("1000.00");
        UUID old = newRow(account, "2026-10-20", "DEBIT", "100.00", false);

        service.update(userId, old, edit("2026-11-05", "DEBIT", "100.00", null, account, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("900.00");
        assertThat(rowOf(old).get("affects_balance")).isEqualTo(true);

        UUID cardRow = newCardRow(newCard(), "2026-11-06", null, null, null, false);
        service.update(userId, cardRow, edit("2026-11-06", "DEBIT", "50.00", null, account, null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("850.00");
        Map<String, Object> saved = rowOf(cardRow);
        assertThat(saved.get("affects_balance")).isEqualTo(true);
        assertThat(saved.get("credit_card_id")).isNull();
        assertThat(transactionSnapshots(account)).isEqualTo(2);
    }

    @Test
    void cardOrCategoryOfAnotherUserOrRemovedIs404WithNothingWritten() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);
        UUID removedCard = newCard();
        jdbc.update("UPDATE credit_cards SET deleted_at = now() WHERE id = ?", removedCard);
        UUID removedCategory = newCategory("Mercado", newCategory("Alimentação", null));
        jdbc.update("UPDATE categories SET deleted_at = now() WHERE id = ?", removedCategory);
        UUID me = userId;
        userId = newUser();
        UUID otherCard = newCard();
        UUID otherCategory = newCategory("Mercado", newCategory("Alimentação", null));
        userId = me;

        for (UUID card : List.of(removedCard, otherCard)) {
            assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", null, null, card),
                IP)).isInstanceOf(ResourceNotFoundException.class);
        }
        for (UUID category : List.of(removedCategory, otherCategory)) {
            assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", category, account,
                null), IP)).isInstanceOf(ResourceNotFoundException.class);
        }
        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
        Map<String, Object> saved = rowOf(row);
        assertThat(saved.get("account_id")).isEqualTo(account);
        assertThat(saved.get("category_id")).isNull();
        assertThat(auditOf(TransactionService.AUDIT_EDITED)).isEmpty();
    }

    @Test
    void rowOfARemovedAccountOrCardCanStillBeEdited() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);
        jdbc.update("UPDATE accounts SET deleted_at = now() WHERE id = ?", account);
        UUID card = newCard();
        UUID cardRow = newCardRow(card, "2026-11-06", null, null, null, false);
        jdbc.update("UPDATE credit_cards SET deleted_at = now() WHERE id = ?", card);

        service.update(userId, row, new TransactionUpdateRequest(LocalDate.parse("2026-11-05"), "PADARIA",
            new BigDecimal("300.00"), "DEBIT", null, account, null), IP);
        service.update(userId, cardRow, edit("2026-11-06", "DEBIT", "100.00", null, null, card), IP);

        assertThat(rowOf(row).get("description")).isEqualTo("PADARIA");
        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
        assertThat(transactionSnapshots(account)).isZero();
        assertThat(rowOf(cardRow).get("description")).isEqualTo("MERCADO");
    }

    @Test
    void amountOutOfRangeIs400WithTheLimitMessage() {
        UUID account = newAccount("1000.00");
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);

        assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "10000000000000.00", null,
            account, null), IP)).isInstanceOf(BadRequestException.class)
            .hasMessage(TransactionService.AMOUNT_OUT_OF_RANGE_MESSAGE);
        assertThatThrownBy(() -> service.update(userId, row, edit("2026-11-05", "DEBIT", "0", null, account, null), IP))
            .isInstanceOf(BadRequestException.class).hasMessage(TransactionService.AMOUNT_MESSAGE);
    }

    @Test
    void installmentKeepsItsCardAndTypeButChangesItsOwnFields() {
        UUID card = newCard();
        UUID group = UUID.randomUUID();
        UUID third = newCardRow(card, "2026-11-02", 3, 10, group, false);
        UUID fourth = newCardRow(card, "2026-12-02", 4, 10, group, true);
        UUID account = newAccount("1000.00");

        for (TransactionUpdateRequest request : List.of(edit("2026-11-02", "DEBIT", "100.00", null, null, newCard()),
            edit("2026-11-02", "DEBIT", "100.00", null, account, null),
            edit("2026-11-02", "CREDIT", "100.00", null, null, card))) {
            assertThatThrownBy(() -> service.update(userId, third, request, IP))
                .isInstanceOf(BadRequestException.class).hasMessage(TransactionService.INSTALLMENT_TARGET_MESSAGE);
        }
        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");

        service.update(userId, third, edit("2026-11-03", "DEBIT", "120.00", null, null, card), IP);

        assertThat(rowOf(third).get("amount")).isEqualTo(new BigDecimal("120.00"));
        assertThat(rowOf(third).get("installment_number")).isEqualTo(3);
        assertThat(rowOf(fourth).get("amount")).isEqualTo(new BigDecimal("100.00"));
        assertThat(rowOf(fourth).get("transaction_date").toString()).isEqualTo("2026-12-02");
    }

    @Test
    void newCategoryOfAnInstallmentGoesToTheFuturesOfItsGroupOnly() {
        UUID card = newCard();
        UUID parent = newCategory("Compras", null);
        UUID oldCategory = newCategory("Roupas", parent);
        UUID newCategory = newCategory("Casa", parent);
        UUID group = UUID.randomUUID();
        UUID second = newCardRow(card, "2026-10-02", 2, 10, group, false);
        UUID third = newCardRow(card, "2026-11-02", 3, 10, group, false);
        List<UUID> futures = new ArrayList<>();
        for (int n = 4; n <= 10; n++) {
            futures.add(newCardRow(card, LocalDate.parse("2026-11-02").plusMonths(n - 3).toString(), n, 10, group,
                true));
        }
        UUID removedFuture = futures.remove(futures.size() - 1);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = ?", removedFuture);
        UUID otherGroupFuture = newCardRow(card, "2026-12-02", 2, 3, UUID.randomUUID(), true);
        jdbc.update("UPDATE transactions SET category_id = ?, classification_source = 'AI', "
            + "classification_confidence = 80 WHERE user_id = ?", oldCategory, userId);

        // no category change: the futures are untouched
        service.update(userId, third, edit("2026-11-03", "DEBIT", "120.00", oldCategory, null, card), IP);
        assertThat(rowOf(futures.get(0)).get("classification_source")).isEqualTo("AI");
        assertThat(auditOf(TransactionService.AUDIT_EDITED)).singleElement().asString()
            .contains("\"future_installments_changed\":0");

        TransactionResponse response = service.update(userId, third,
            edit("2026-11-03", "DEBIT", "120.00", newCategory, null, card), IP);

        assertThat(response.remainingInstallments()).isEqualTo(6);
        for (UUID future : futures) {
            assertThat(rowOf(future)).containsEntry("category_id", newCategory)
                .containsEntry("classification_source", "USER").containsEntry("classification_confidence", 100);
        }
        assertThat(rowOf(third).get("category_id")).isEqualTo(newCategory);
        assertThat(rowOf(second)).containsEntry("category_id", oldCategory).containsEntry("classification_source", "AI");
        assertThat(rowOf(removedFuture).get("category_id")).isEqualTo(oldCategory);
        assertThat(rowOf(otherGroupFuture).get("category_id")).isEqualTo(oldCategory);
        assertThat(auditOf(TransactionService.AUDIT_EDITED)).anySatisfy(m -> assertThat(m)
            .contains("\"future_installments_changed\":6"));

        // no category also goes to the futures, as "Não classificado"
        service.update(userId, third, edit("2026-11-03", "DEBIT", "120.00", null, null, card), IP);
        for (UUID future : futures) {
            assertThat(rowOf(future)).containsEntry("category_id", null)
                .containsEntry("classification_source", "NONE").containsEntry("classification_confidence", 0);
        }
        assertThat(rowOf(second).get("category_id")).isEqualTo(oldCategory);
    }

    @Test
    void removingTheOnlyConfirmedInstallmentTakesTheFuturesAlong() {
        UUID card = newCard();
        UUID group = UUID.randomUUID();
        UUID third = newCardRow(card, "2026-11-02", 3, 10, group, false);
        for (int n = 4; n <= 10; n++) {
            newCardRow(card, LocalDate.parse("2026-11-02").plusMonths(n - 3).toString(), n, 10, group, true);
        }
        assertThat(service.list(userId, "2026-11")).singleElement()
            .satisfies(r -> assertThat(r.confirmedInstallments()).isEqualTo(1));

        service.delete(userId, third, IP);

        assertThat(rowOf(third).get("deleted_at")).isNotNull();
        assertThat(rowOf(third).get("installment_futures_removed_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE installment_group_id = ? "
            + "AND deleted_at IS NULL", Integer.class, group)).isZero();
        assertThat(auditOf(TransactionService.AUDIT_DELETED)).singleElement().asString()
            .contains("\"removed_installments\":7");
    }

    @Test
    void removingOneOfTwoConfirmedInstallmentsKeepsTheFutures() {
        UUID card = newCard();
        UUID group = UUID.randomUUID();
        newCardRow(card, "2026-10-02", 2, 10, group, false);
        UUID third = newCardRow(card, "2026-11-02", 3, 10, group, false);
        UUID fourth = newCardRow(card, "2026-12-02", 4, 10, group, true);
        assertThat(service.list(userId, "2026-11")).singleElement()
            .satisfies(r -> assertThat(r.confirmedInstallments()).isEqualTo(2));

        service.delete(userId, third, IP);

        assertThat(rowOf(fourth).get("deleted_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE installment_group_id = ? "
            + "AND installment_futures_removed_at IS NOT NULL", Integer.class, group)).isZero();
    }

    // Story 5.13: adding by hand

    private TransactionCreateRequest add(String date, String type, String amount, UUID categoryId, UUID accountId,
                                         UUID cardId) {
        return new TransactionCreateRequest(LocalDate.parse(date), "Feira", new BigDecimal(amount), type, categoryId,
            accountId, cardId);
    }

    private int rowsOfUser() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void debitOnAnAccountAfterTheReferenceEntersTheBalanceConfirmedAndTeachesNothing() {
        UUID account = newAccount("1000.00");
        UUID category = newCategory("Feira", newCategory("Alimentação", null));

        TransactionResponse response = service.create(userId, add("2026-11-05", "DEBIT", "50.00", category, account,
            null), IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("950.00");
        assertThat(transactionSnapshots(account)).isEqualTo(1);
        Map<String, Object> saved = rowOf(response.id());
        assertThat(saved.get("user_id")).isEqualTo(userId);
        assertThat(saved.get("account_id")).isEqualTo(account);
        assertThat(saved.get("credit_card_id")).isNull();
        assertThat(saved.get("import_id")).isNull();
        assertThat(saved.get("external_id")).isNull();
        assertThat(saved.get("description")).isEqualTo("Feira");
        assertThat(saved.get("original_description")).isEqualTo("Feira");
        assertThat(saved.get("kind")).isEqualTo("REGULAR");
        assertThat(saved.get("classification_source")).isEqualTo("USER");
        assertThat(saved.get("classification_confidence")).isEqualTo(100);
        assertThat(saved.get("review_status")).isEqualTo("CONFIRMED");
        assertThat(saved.get("affects_balance")).isEqualTo(true);
        assertThat(saved.get("is_pix")).isEqualTo(false);
        assertThat(saved.get("is_installment")).isEqualTo(false);
        assertThat(response.category()).isNotNull();
        assertThat(service.list(userId, "2026-11")).extracting(TransactionResponse::id).contains(response.id());
        assertThat(auditOf(TransactionService.AUDIT_CREATED)).singleElement().asString()
            .contains(response.id().toString()).doesNotContain("Feira");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_classification_patterns WHERE user_id = ?",
            Integer.class, userId)).isZero();
    }

    @Test
    void dateOnOrBeforeTheReferenceOrACardLeavesEveryBalanceAlone() {
        UUID account = newAccount("1000.00");
        UUID card = newCard();
        UUID category = newCategory("Feira", newCategory("Alimentação", null));

        TransactionResponse reconciled = service.create(userId, add("2026-11-01", "DEBIT", "50.00", category, account,
            null), IP);
        TransactionResponse onCard = service.create(userId, add("2026-11-05", "CREDIT", "30.00", category, null, card),
            IP);

        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
        assertThat(transactionSnapshots(account)).isZero();
        assertThat(rowOf(reconciled.id()).get("affects_balance")).isEqualTo(false);
        assertThat(rowOf(onCard.id()).get("affects_balance")).isEqualTo(false);
        assertThat(rowOf(onCard.id()).get("credit_card_id")).isEqualTo(card);
    }

    @Test
    void missingCategoryGroupOrTargetIs400WithNothingWritten() {
        UUID account = newAccount("1000.00");
        UUID card = newCard();
        UUID group = newCategory("Alimentação", null);
        UUID category = newCategory("Feira", group);

        for (TransactionCreateRequest request : List.of(
            add("2026-11-05", "DEBIT", "50.00", null, account, null),
            add("2026-11-05", "DEBIT", "50.00", group, account, null),
            add("2026-11-05", "DEBIT", "50.00", category, account, card),
            add("2026-11-05", "DEBIT", "50.00", category, null, null))) {
            assertThatThrownBy(() -> service.create(userId, request, IP)).isInstanceOf(BadRequestException.class);
        }
        assertThat(rowsOfUser()).isZero();
        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
    }

    @Test
    void targetOrCategoryOfAnotherUserOrRemovedIs404WithNothingWritten() {
        UUID account = newAccount("1000.00");
        UUID category = newCategory("Feira", newCategory("Alimentação", null));
        UUID removedAccount = newAccount("1000.00");
        jdbc.update("UPDATE accounts SET deleted_at = now() WHERE id = ?", removedAccount);
        UUID removedCard = newCard();
        jdbc.update("UPDATE credit_cards SET deleted_at = now() WHERE id = ?", removedCard);
        UUID removedCategory = newCategory("Mercado", newCategory("Casa", null));
        jdbc.update("UPDATE categories SET deleted_at = now() WHERE id = ?", removedCategory);
        UUID me = userId;
        userId = newUser();
        UUID otherAccount = newAccount("1000.00");
        UUID otherCard = newCard();
        UUID otherCategory = newCategory("Feira", newCategory("Alimentação", null));
        userId = me;

        for (UUID target : List.of(removedAccount, otherAccount)) {
            assertThatThrownBy(() -> service.create(userId, add("2026-11-05", "DEBIT", "50.00", category, target, null),
                IP)).isInstanceOf(ResourceNotFoundException.class);
        }
        for (UUID card : List.of(removedCard, otherCard)) {
            assertThatThrownBy(() -> service.create(userId, add("2026-11-05", "DEBIT", "50.00", category, null, card),
                IP)).isInstanceOf(ResourceNotFoundException.class);
        }
        for (UUID other : List.of(removedCategory, otherCategory)) {
            assertThatThrownBy(() -> service.create(userId, add("2026-11-05", "DEBIT", "50.00", other, account, null),
                IP)).isInstanceOf(ResourceNotFoundException.class);
        }
        assertThat(rowsOfUser()).isZero();
        assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(otherAccount)).isEqualByComparingTo("1000.00");
        assertThat(transactionSnapshots(account)).isZero();
        assertThat(auditOf(TransactionService.AUDIT_CREATED)).isEmpty();
    }

    @Test
    void balanceOutOfRangeOnCreateIs400WithNothingWritten() {
        UUID account = newAccount("-9999999999999.00");
        UUID category = newCategory("Feira", newCategory("Alimentação", null));

        assertThatThrownBy(() -> service.create(userId, add("2026-11-05", "DEBIT", "10.00", category, account, null),
            IP)).isInstanceOf(BadRequestException.class);
        assertThat(balanceOf(account)).isEqualByComparingTo("-9999999999999.00");
        assertThat(rowsOfUser()).isZero();
    }

    // Retro Epic 5, item 25: writes by hand take the advisory lock of their target, like an import of it

    /** Holds the advisory lock of {@code target} in another transaction until {@code release}. */
    private CompletableFuture<Void> holdingLockOf(UUID target, CountDownLatch release) throws InterruptedException {
        CountDownLatch held = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            transactionImportRepository.lockTarget(TransactionCore.lockKey(target));
            held.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        return holder;
    }

    @Test
    void createOnACardWaitsForTheAdvisoryLockOfTheCard() throws Exception {
        UUID card = newCard();
        UUID category = newCategory("Feira", newCategory("Alimentação", null));
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = holdingLockOf(card, release);

            CompletableFuture<TransactionResponse> create = CompletableFuture.supplyAsync(
                () -> service.create(userId, add("2026-11-05", "DEBIT", "30.00", category, null, card), IP));

            assertThatThrownBy(() -> create.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(rowsOfUser()).isZero();
            release.countDown();
            create.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
            assertThat(rowsOfUser()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void movingToACardWaitsForTheAdvisoryLockOfTheCard() throws Exception {
        UUID account = newAccount("1000.00");
        UUID card = newCard();
        UUID row = newRow(account, "2026-11-05", "DEBIT", "100.00", true);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<Void> holder = holdingLockOf(card, release);

            CompletableFuture<TransactionResponse> update = CompletableFuture.supplyAsync(
                () -> service.update(userId, row, edit("2026-11-05", "DEBIT", "100.00", null, null, card), IP));

            assertThatThrownBy(() -> update.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(rowOf(row).get("account_id")).isEqualTo(account);
            release.countDown();
            update.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
            assertThat(rowOf(row).get("credit_card_id")).isEqualTo(card);
            assertThat(balanceOf(account)).isEqualByComparingTo("1100.00");
        } finally {
            release.countDown();
        }
    }
}
