package com.porquinho.service;

import com.porquinho.dto.ImportReviewActionResponse;
import com.porquinho.dto.ImportTransactionResponse;
import com.porquinho.dto.UpdateImportCategoryRequest;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stories 5.9 and 5.10: the review and the conclusion of an import against the real schema (PostgreSQL via
 * Testcontainers, Flyway through V29). Not @Transactional: the concurrent conclusions really race.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionImportReviewServiceTest {

    private static final String IP = "127.0.0.1";

    @Autowired
    private TransactionImportReviewService reviewService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.porquinho.repository.UserClassificationPatternRepository patternRepository;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    /** Never the real client: nothing here goes to the AI. */
    @MockitoBean
    private TransactionClassifier classifier;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        cardId = newCard(userId);
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

    private UUID newCard(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO credit_cards (id, user_id, card_name, card_limit, due_day) "
            + "VALUES (?, ?, 'Nubank', 1000, 10)", id, owner);
        return id;
    }

    private UUID newImport(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, credit_card_id, file_name, file_type, file_size_kb, "
            + "status) VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, ?)", id, userId, cardId, status);
        return id;
    }

    private UUID newCategory(String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }

    /** A pending REGULAR row of {@code importId}. */
    private UUID newRow(UUID importId, Integer confidence, UUID categoryId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, credit_card_id, import_id, external_id, transaction_date, "
            + "description, original_description, amount, transaction_type, category_id, classification_source, "
            + "classification_confidence) VALUES (?, ?, ?, ?, ?, DATE '2026-11-03', 'LOJA', 'LOJA', 10.50, 'DEBIT', ?, "
            + "'AI', ?)", id, userId, cardId, importId, "fit-" + id, categoryId, confidence);
        return id;
    }

    private Map<String, Object> rowOf(UUID id) {
        return jdbc.queryForMap("SELECT * FROM transactions WHERE id = ?", id);
    }

    private String statusOf(UUID importId) {
        return jdbc.queryForObject("SELECT status FROM transaction_imports WHERE id = ?", String.class, importId);
    }

    private List<String> auditOf(String event) {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = ?", String.class,
            userId, event);
    }

    @Test
    void listResolvesTheCategoryAndBringsTheMarkers() {
        UUID review = newImport("REVIEW");
        UUID group = newCategory("Moradia", null);
        UUID child = newCategory("Aluguel", group);
        UUID row = newRow(review, 92, child);
        jdbc.update("UPDATE transactions SET is_pix = TRUE, pix_details = ?::jsonb, is_installment = TRUE, "
                + "installment_number = 3, installment_total = 10 WHERE id = ?",
            "{\"detected\":true,\"direction\":\"SENT\",\"counterpart_name\":\"MARIA\"}", row);

        List<ImportTransactionResponse> rows = reviewService.list(userId, review);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(row);
            assertThat(r.date()).hasToString("2026-11-03");
            assertThat(r.description()).isEqualTo("LOJA");
            assertThat(r.amount()).isEqualByComparingTo("10.50");
            assertThat(r.type()).isEqualTo("DEBIT");
            assertThat(r.kind()).isEqualTo("REGULAR");
            assertThat(r.category().id()).isEqualTo(child);
            assertThat(r.category().name()).isEqualTo("Aluguel");
            assertThat(r.category().group()).isEqualTo("Moradia");
            assertThat(r.source()).isEqualTo("AI");
            assertThat(r.confidence()).isEqualTo(92);
            assertThat(r.reviewStatus()).isEqualTo("PENDING");
            assertThat(r.isPix()).isTrue();
            assertThat(r.pixDetails().get("counterpart_name").asText()).isEqualTo("MARIA");
            assertThat(r.isInstallment()).isTrue();
            assertThat(r.installmentNumber()).isEqualTo(3);
            assertThat(r.installmentTotal()).isEqualTo(10);
            assertThat(r.isFutureInstallment()).isFalse();
        });
    }

    @Test
    void removedCategoryKeepsItsNameAndGroup() {
        UUID review = newImport("REVIEW");
        UUID group = newCategory("Lazer", null);
        UUID child = newCategory("Cinema", group);
        newRow(review, 60, child);
        jdbc.update("UPDATE categories SET deleted_at = now() WHERE user_id = ?", userId);

        assertThat(reviewService.list(userId, review)).singleElement().satisfies(r -> {
            assertThat(r.category().name()).isEqualTo("Cinema");
            assertThat(r.category().group()).isEqualTo("Lazer");
        });
    }

    @Test
    void rowWithoutCategoryOrConfidenceComesAsItIs() {
        UUID review = newImport("REVIEW");
        newRow(review, null, null);

        assertThat(reviewService.list(userId, review)).singleElement().satisfies(r -> {
            assertThat(r.category()).isNull();
            assertThat(r.confidence()).isNull();
            assertThat(r.pixDetails()).isNull();
        });
    }

    @Test
    void listLeavesFuturesAndRemovedRowsOutAndKeepsTheCardPayment() {
        UUID review = newImport("REVIEW");
        UUID regular = newRow(review, 92, newCategory("Geral", null));
        UUID payment = newRow(review, null, null);
        jdbc.update("UPDATE transactions SET kind = 'CARD_PAYMENT' WHERE id = ?", payment);
        UUID future = newRow(review, 92, null);
        jdbc.update("UPDATE transactions SET is_future_installment = TRUE WHERE id = ?", future);
        UUID removed = newRow(review, 92, null);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = ?", removed);

        assertThat(reviewService.list(userId, review)).extracting(ImportTransactionResponse::id)
            .containsExactlyInAnyOrder(regular, payment);
    }

    @Test
    void importOfAnotherUserIs404InTheThreeRoutesAndNothingIsWritten() {
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 92, newCategory("Geral", null));
        UUID stranger = newUser();

        assertThatThrownBy(() -> reviewService.list(stranger, review)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reviewService.approveHighConfidence(stranger, review, IP))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reviewService.discard(stranger, review, IP))
            .isInstanceOf(ResourceNotFoundException.class);

        assertThat(statusOf(review)).isEqualTo("REVIEW");
        assertThat(rowOf(row).get("review_status")).isEqualTo("PENDING");
        assertThat(rowOf(row).get("deleted_at")).isNull();
    }

    @Test
    void approvalTakesOnlyRegularRowsOf80OrMoreWithCategory() {
        UUID review = newImport("REVIEW");
        UUID category = newCategory("Geral", null);
        UUID high = newRow(review, 92, category);
        UUID limit = newRow(review, 80, category);
        UUID below = newRow(review, 79, category);
        UUID empty = newRow(review, null, category);
        UUID withoutCategory = newRow(review, 95, null);
        UUID payment = newRow(review, 95, category);
        jdbc.update("UPDATE transactions SET kind = 'CARD_PAYMENT' WHERE id = ?", payment);
        UUID future = newRow(review, 95, category);
        jdbc.update("UPDATE transactions SET is_future_installment = TRUE WHERE id = ?", future);
        UUID removed = newRow(review, 95, category);
        jdbc.update("UPDATE transactions SET deleted_at = now() WHERE id = ?", removed);
        UUID confirmed = newRow(review, 95, category);
        jdbc.update("UPDATE transactions SET review_status = 'CONFIRMED' WHERE id = ?", confirmed);

        ImportReviewActionResponse response = reviewService.approveHighConfidence(userId, review, IP);

        assertThat(response.affectedTransactions()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("REVIEW");
        assertThat(rowOf(high).get("review_status")).isEqualTo("APPROVED");
        assertThat(rowOf(limit).get("review_status")).isEqualTo("APPROVED");
        for (UUID untouched : List.of(below, empty, withoutCategory, payment, future, removed)) {
            assertThat(rowOf(untouched).get("review_status")).isEqualTo("PENDING");
        }
        assertThat(rowOf(confirmed).get("review_status")).isEqualTo("CONFIRMED");
        assertThat(rowOf(high).get("affects_balance")).isEqualTo(false);
        assertThat(statusOf(review)).isEqualTo("REVIEW");
        assertThat(auditOf(TransactionImportReviewService.AUDIT_HIGH_CONFIDENCE_APPROVED)).singleElement().asString()
            .isEqualTo("{\"import_id\":\"" + review + "\",\"approved_transactions\":2}");
    }

    @Test
    void secondApprovalChangesNothing() {
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 92, newCategory("Geral", null));
        reviewService.approveHighConfidence(userId, review, IP);
        Object updatedAt = rowOf(row).get("updated_at");

        ImportReviewActionResponse again = reviewService.approveHighConfidence(userId, review, IP);

        assertThat(again.affectedTransactions()).isZero();
        assertThat(rowOf(row).get("updated_at")).isEqualTo(updatedAt);
        assertThat(auditOf(TransactionImportReviewService.AUDIT_HIGH_CONFIDENCE_APPROVED)).hasSize(1);
    }

    @Test
    void approvingOrDiscardingOutsideReviewIs409AndNothingChanges() {
        UUID category = newCategory("Geral", null);
        for (String status : List.of("PROCESSING", "COMPLETED", "FAILED", "DISCARDED")) {
            UUID outside = newImport(status);
            UUID row = newRow(outside, 92, category);

            assertThatThrownBy(() -> reviewService.approveHighConfidence(userId, outside, IP))
                .isInstanceOf(ConflictException.class).hasMessage("Esta importação não está em revisão");
            assertThatThrownBy(() -> reviewService.discard(userId, outside, IP))
                .isInstanceOf(ConflictException.class).hasMessage("Esta importação não está em revisão");

            assertThat(statusOf(outside)).isEqualTo(status);
            assertThat(rowOf(row).get("review_status")).isEqualTo("PENDING");
            assertThat(rowOf(row).get("deleted_at")).isNull();
        }
        assertThat(auditOf(TransactionImportReviewService.AUDIT_DISCARDED)).isEmpty();
    }

    @Test
    void discardRemovesTheRowsAndTheFuturesOfTheImport() {
        UUID review = newImport("REVIEW");
        UUID other = newImport("REVIEW");
        UUID row = newRow(review, 92, null);
        UUID future = newRow(review, 92, null);
        jdbc.update("UPDATE transactions SET is_future_installment = TRUE WHERE id = ?", future);
        UUID untouched = newRow(other, 92, null);

        ImportReviewActionResponse response = reviewService.discard(userId, review, IP);

        assertThat(response.status()).isEqualTo("DISCARDED");
        assertThat(response.affectedTransactions()).isEqualTo(2);
        assertThat(statusOf(review)).isEqualTo("DISCARDED");
        assertThat(rowOf(row).get("deleted_at")).isNotNull();
        assertThat(rowOf(future).get("deleted_at")).isNotNull();
        assertThat(rowOf(untouched).get("deleted_at")).isNull();
        assertThat(reviewService.list(userId, review)).isEmpty();
        assertThat(auditOf(TransactionImportReviewService.AUDIT_DISCARDED)).singleElement().asString()
            .contains("\"import_id\":\"" + review + "\"").contains("\"discarded_transactions\":2")
            .contains("\"installments_transferred\":0").contains("\"installments_reverted\":0")
            .doesNotContain("LOJA");
    }

    @Test
    void discardingTheCreatorMovesTheConfirmedRowToACompletedConfirmer() {
        UUID creator = newImport("REVIEW");
        UUID confirmer = newImport("COMPLETED");
        UUID sibling = newRow(creator, 92, null);
        UUID confirmed = newRow(creator, 92, null);
        jdbc.update("UPDATE transactions SET confirmed_by_import_id = ? WHERE id = ?", confirmer, confirmed);

        reviewService.discard(userId, creator, IP);

        Map<String, Object> row = rowOf(confirmed);
        assertThat(row.get("import_id")).isEqualTo(confirmer);
        assertThat(row.get("confirmed_by_import_id")).isNull();
        assertThat(row.get("review_status")).isEqualTo("CONFIRMED");
        assertThat(row.get("deleted_at")).isNull();
        assertThat(rowOf(sibling).get("deleted_at")).isNotNull();
    }

    @Test
    void discardingTheCreatorMovesTheConfirmedRowToTheReviewOfTheConfirmer() {
        UUID creator = newImport("REVIEW");
        UUID confirmer = newImport("REVIEW");
        UUID confirmed = newRow(creator, 92, null);
        jdbc.update("UPDATE transactions SET confirmed_by_import_id = ? WHERE id = ?", confirmer, confirmed);

        reviewService.discard(userId, creator, IP);

        assertThat(rowOf(confirmed).get("review_status")).isEqualTo("PENDING");
        assertThat(reviewService.list(userId, confirmer)).extracting(ImportTransactionResponse::id)
            .containsExactly(confirmed);

        // now it belongs to the confirmer: discarding it removes the row, it does not make it future
        reviewService.discard(userId, confirmer, IP);

        assertThat(rowOf(confirmed).get("deleted_at")).isNotNull();
        assertThat(rowOf(confirmed).get("is_future_installment")).isEqualTo(false);
    }

    @Test
    void discardingTheConfirmerUndoesTheApprovalButNotAConfirmation() {
        UUID creator = newImport("REVIEW");
        UUID confirmer = newImport("REVIEW");
        UUID approved = newRow(creator, 92, newCategory("Geral", null));
        UUID confirmed = newRow(creator, 92, null);
        jdbc.update("UPDATE transactions SET confirmed_by_import_id = ? WHERE import_id = ?", confirmer, creator);
        jdbc.update("UPDATE transactions SET review_status = 'CONFIRMED' WHERE id = ?", confirmed);
        reviewService.approveHighConfidence(userId, creator, IP);
        assertThat(rowOf(approved).get("review_status")).isEqualTo("APPROVED");

        reviewService.discard(userId, confirmer, IP);

        assertThat(rowOf(approved).get("is_future_installment")).isEqualTo(true);
        assertThat(rowOf(approved).get("review_status")).isEqualTo("PENDING");
        assertThat(rowOf(confirmed).get("is_future_installment")).isEqualTo(true);
        assertThat(rowOf(confirmed).get("review_status")).isEqualTo("CONFIRMED");
    }

    @Test
    void discardingTheConfirmerMakesTheRowFutureAgainWithTheDataOfTheFile() {
        UUID creator = newImport("REVIEW");
        UUID confirmer = newImport("REVIEW");
        UUID confirmed = newRow(creator, 92, null);
        jdbc.update("UPDATE transactions SET confirmed_by_import_id = ? WHERE id = ?", confirmer, confirmed);
        UUID ownRow = newRow(confirmer, 92, null);

        reviewService.discard(userId, confirmer, IP);

        Map<String, Object> row = rowOf(confirmed);
        assertThat(row.get("import_id")).isEqualTo(creator);
        assertThat(row.get("is_future_installment")).isEqualTo(true);
        assertThat(row.get("external_id")).isNull();
        assertThat(row.get("confirmed_by_import_id")).isNull();
        assertThat(row.get("deleted_at")).isNull();
        assertThat(row.get("description")).isEqualTo("LOJA");
        assertThat(row.get("transaction_date").toString()).isEqualTo("2026-11-03");
        assertThat(row.get("amount").toString()).isEqualTo("10.50");
        assertThat(rowOf(ownRow).get("deleted_at")).isNotNull();
        assertThat(reviewService.list(userId, creator)).isEmpty();
        assertThat(auditOf(TransactionImportReviewService.AUDIT_DISCARDED)).singleElement().asString()
            .contains("\"discarded_transactions\":1").contains("\"installments_reverted\":1");
    }

    // ---- Story 5.10: changing categories ----

    /** A child category of a new group. */
    private UUID newChildCategory() {
        return newCategory("Mercado " + UUID.randomUUID(), newCategory("Grupo " + UUID.randomUUID(), null));
    }

    private UpdateImportCategoryRequest edit(UUID categoryId, UUID... rows) {
        return new UpdateImportCategoryRequest(List.of(rows), categoryId);
    }

    /** Marks {@code row} as installment 3/10 of {@code group}. */
    private void asInstallment(UUID row, UUID group) {
        jdbc.update("UPDATE transactions SET is_installment = TRUE, installment_number = 3, installment_total = 10, "
            + "installment_group_id = ? WHERE id = ?", group, row);
    }

    /** A future installment of {@code group} in {@code importId}. */
    private UUID newFuture(UUID importId, UUID group, UUID categoryId) {
        UUID future = newRow(importId, 60, categoryId);
        asInstallment(future, group);
        jdbc.update("UPDATE transactions SET is_future_installment = TRUE WHERE id = ?", future);
        return future;
    }

    private void assertReviewedByUser(UUID row, UUID categoryId) {
        assertThat(rowOf(row)).containsEntry("category_id", categoryId).containsEntry("classification_source", "USER")
            .containsEntry("classification_confidence", 100).containsEntry("review_status", "APPROVED");
    }

    private void assertUntouched(UUID row, UUID categoryId) {
        assertThat(rowOf(row)).containsEntry("category_id", categoryId).containsEntry("classification_source", "AI")
            .containsEntry("review_status", "PENDING");
    }

    @Test
    void editingARowMarksItAsReviewedByTheUserAndAuditsOnlyCounts() {
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 60, newChildCategory());
        UUID other = newRow(review, 60, null);
        UUID category = newChildCategory();

        ImportReviewActionResponse response = reviewService.updateCategory(userId, review, edit(category, row), IP);

        assertThat(response.status()).isEqualTo("REVIEW");
        assertThat(response.affectedTransactions()).isEqualTo(1);
        assertReviewedByUser(row, category);
        assertUntouched(other, null);
        assertThat(auditOf(TransactionImportReviewService.AUDIT_CATEGORY_CHANGED)).singleElement().asString()
            .contains(review.toString()).contains("\"changed_transactions\":1")
            .contains("\"future_installments_changed\":0").doesNotContain(category.toString());
    }

    @Test
    void savingTheSameCategoryCountsAsReviewed() {
        UUID review = newImport("REVIEW");
        UUID category = newChildCategory();
        UUID row = newRow(review, 60, category);

        reviewService.updateCategory(userId, review, edit(category, row), IP);

        assertReviewedByUser(row, category);
    }

    @Test
    void bulkEditChangesEveryRowAndARepeatedIdCountsOnce() {
        UUID review = newImport("REVIEW");
        UUID first = newRow(review, 60, null);
        UUID second = newRow(review, 30, null);
        UUID category = newChildCategory();

        ImportReviewActionResponse response = reviewService.updateCategory(userId, review,
            edit(category, first, second, first), IP);

        assertThat(response.affectedTransactions()).isEqualTo(2);
        assertReviewedByUser(first, category);
        assertReviewedByUser(second, category);
    }

    @Test
    void bulkEditWithARowOutOfTheReviewIs404AndWritesNothing() {
        UUID review = newImport("REVIEW");
        UUID valid = newRow(review, 60, null);
        UUID fromOtherImport = newRow(newImport("REVIEW"), 60, null);
        UUID future = newFuture(review, UUID.randomUUID(), null);
        UUID cardPayment = newRow(review, null, null);
        jdbc.update("UPDATE transactions SET kind = 'CARD_PAYMENT' WHERE id = ?", cardPayment);
        UUID removed = newRow(review, 60, null);
        jdbc.update("UPDATE transactions SET deleted_at = NOW() WHERE id = ?", removed);
        UUID category = newChildCategory();

        for (UUID invalid : List.of(fromOtherImport, future, cardPayment, removed, UUID.randomUUID())) {
            assertThatThrownBy(() -> reviewService.updateCategory(userId, review, edit(category, valid, invalid), IP))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        assertUntouched(valid, null);
        assertThat(auditOf(TransactionImportReviewService.AUDIT_CATEGORY_CHANGED)).isEmpty();
    }

    @Test
    void editingAnInstallmentGoesToTheFuturesOfTheSamePurchaseAndImport() {
        UUID review = newImport("REVIEW");
        UUID group = UUID.randomUUID();
        UUID row = newRow(review, 60, null);
        asInstallment(row, group);
        List<UUID> futures = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            futures.add(newFuture(review, group, null));
        }
        UUID otherPurchase = newFuture(review, UUID.randomUUID(), null);
        UUID otherImport = newFuture(newImport("REVIEW"), group, null);
        UUID category = newChildCategory();

        reviewService.updateCategory(userId, review, edit(category, row), IP);

        assertReviewedByUser(row, category);
        for (UUID future : futures) {
            assertThat(rowOf(future)).containsEntry("category_id", category)
                .containsEntry("classification_source", "USER").containsEntry("classification_confidence", 100)
                .containsEntry("review_status", "PENDING");
        }
        assertUntouched(otherPurchase, null);
        assertUntouched(otherImport, null);
        assertThat(auditOf(TransactionImportReviewService.AUDIT_CATEGORY_CHANGED)).singleElement().asString()
            .contains("\"future_installments_changed\":7");
    }

    @Test
    void editingOutsideReviewIs409() {
        UUID category = newChildCategory();
        for (String status : List.of("COMPLETED", "DISCARDED", "PROCESSING", "FAILED")) {
            UUID closed = newImport(status);
            UUID row = newRow(closed, 60, null);

            assertThatThrownBy(() -> reviewService.updateCategory(userId, closed, edit(category, row), IP))
                .isInstanceOf(ConflictException.class);

            assertUntouched(row, null);
        }
    }

    @Test
    void aGroupIs400AndARemovedOrForeignCategoryIs404() {
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 60, null);
        UUID group = newCategory("Moradia", null);
        UUID removed = newChildCategory();
        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE id = ?", removed);
        UUID me = userId;
        userId = newUser();
        UUID foreign = newChildCategory();
        userId = me;

        assertThatThrownBy(() -> reviewService.updateCategory(userId, review, edit(group, row), IP))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reviewService.updateCategory(userId, review, edit(removed, row), IP))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reviewService.updateCategory(userId, review, edit(foreign, row), IP))
            .isInstanceOf(ResourceNotFoundException.class);
        assertUntouched(row, null);
    }

    @Test
    void editingAnImportOfAnotherUserIs404() {
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 60, null);
        UUID category = newChildCategory();

        assertThatThrownBy(() -> reviewService.updateCategory(newUser(), review, edit(category, row), IP))
            .isInstanceOf(ResourceNotFoundException.class);

        assertUntouched(row, null);
    }

    // ---- Story 5.10: finishing an import ----

    private UUID newAccount(String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Nubank', 'CHECKING', ?, ?)", id, userId, new BigDecimal(balance), new BigDecimal(balance));
        return id;
    }

    /** An account with an initial snapshot at the instant {@code snapshotAt}. */
    private UUID newAccount(String balance, String snapshotAt) {
        UUID id = newAccount(balance);
        newSnapshot(id, "initial", balance, snapshotAt);
        return id;
    }

    private void newSnapshot(UUID accountId, String event, String balance, String at) {
        jdbc.update("INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, "
            + "created_by_event) VALUES (?, ?, ?, ?::timestamptz, ?)", accountId, userId, new BigDecimal(balance), at, event);
    }

    private UUID newAccountImport(UUID accountId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, status) "
            + "VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, ?)", id, userId, accountId, status);
        return id;
    }

    /** A pending REGULAR row of an account. */
    private UUID newAccountRow(UUID importId, UUID accountId, String date, String type, String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, import_id, external_id, transaction_date, "
            + "description, original_description, amount, transaction_type) VALUES (?, ?, ?, ?, ?, ?::date, 'LOJA', "
            + "'LOJA', ?, ?)", id, userId, accountId, importId, "fit-" + id, date, new BigDecimal(amount), type);
        return id;
    }

    private BigDecimal balanceOf(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private List<Map<String, Object>> transactionSnapshots(UUID accountId) {
        return jdbc.queryForList("SELECT * FROM account_balance_history WHERE account_id = ? "
            + "AND created_by_event = 'transaction'", accountId);
    }

    private boolean affectsBalance(UUID row) {
        return (boolean) rowOf(row).get("affects_balance");
    }

    @Test
    void onlyRowsAfterTheReferenceDateChangeTheBalanceWithOneSnapshot() {
        UUID account = newAccount("1000.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID before = newAccountRow(review, account, "2026-11-02", "DEBIT", "10.00");
        UUID sameDay = newAccountRow(review, account, "2026-11-03", "DEBIT", "20.00");
        UUID after = newAccountRow(review, account, "2026-11-04", "DEBIT", "30.00");
        UUID credit = newAccountRow(review, account, "2026-11-05", "CREDIT", "5.50");

        ImportReviewActionResponse response = reviewService.complete(userId, review, IP);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.affectedTransactions()).isEqualTo(4);
        assertThat(statusOf(review)).isEqualTo("COMPLETED");
        assertThat(balanceOf(account)).isEqualByComparingTo("975.50");
        assertThat(transactionSnapshots(account)).singleElement().satisfies(snapshot ->
            assertThat((BigDecimal) snapshot.get("balance_snapshot")).isEqualByComparingTo("975.50"));
        assertThat(affectsBalance(before)).isFalse();
        assertThat(affectsBalance(sameDay)).isFalse();
        assertThat(affectsBalance(after)).isTrue();
        assertThat(affectsBalance(credit)).isTrue();
        for (UUID row : List.of(before, sameDay, after, credit)) {
            assertThat(rowOf(row)).containsEntry("review_status", "CONFIRMED");
        }
        assertThat(auditOf(TransactionImportReviewService.AUDIT_COMPLETED)).singleElement().asString()
            .contains(review.toString()).contains("\"confirmed_transactions\":4").contains("\"balance_transactions\":2")
            .doesNotContain("975");
    }

    @Test
    void theReferenceInstantIsReadInSaoPaulo() {
        // 01:00 UTC of 04/11 is still 22:00 of 03/11 in São Paulo
        UUID account = newAccount("100.00", "2026-11-04T01:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID sameDay = newAccountRow(review, account, "2026-11-03", "DEBIT", "1.00");
        UUID after = newAccountRow(review, account, "2026-11-04", "DEBIT", "2.00");

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(sameDay)).isFalse();
        assertThat(affectsBalance(after)).isTrue();
        assertThat(balanceOf(account)).isEqualByComparingTo("98.00");
    }

    @Test
    void theReferenceIsTheLastOfEditReconciliationAndInitialSnapshotsButNotOfTransfers() {
        UUID account = newAccount("100.00", "2026-11-01T15:00:00Z");
        newSnapshot(account, "edit", "100.00", "2026-11-02T15:00:00Z");
        newSnapshot(account, "reconciliation", "100.00", "2026-11-03T15:00:00Z");
        newSnapshot(account, "transfer", "100.00", "2026-11-05T15:00:00Z");
        newSnapshot(account, "transaction", "100.00", "2026-11-05T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID sameDay = newAccountRow(review, account, "2026-11-03", "DEBIT", "1.00");
        UUID after = newAccountRow(review, account, "2026-11-04", "DEBIT", "2.00");

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(sameDay)).isFalse();
        assertThat(affectsBalance(after)).isTrue();
    }

    @Test
    void aReconciliationWithoutDifferenceMovesTheReferenceWithoutASnapshot() {
        UUID account = newAccount("100.00", "2026-11-01T15:00:00Z");
        jdbc.update("UPDATE accounts SET last_reconciliation_date = '2026-11-05T15:00:00Z'::timestamptz WHERE id = ?",
            account);
        UUID review = newAccountImport(account, "REVIEW");
        UUID sameDay = newAccountRow(review, account, "2026-11-05", "DEBIT", "1.00");
        UUID after = newAccountRow(review, account, "2026-11-06", "DEBIT", "2.00");

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(sameDay)).isFalse();
        assertThat(affectsBalance(after)).isTrue();
        assertThat(balanceOf(account)).isEqualByComparingTo("98.00");
    }

    @Test
    void anEditSnapshotMovesTheReference() {
        UUID account = newAccount("100.00", "2026-11-01T15:00:00Z");
        newSnapshot(account, "edit", "100.00", "2026-11-05T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID sameDay = newAccountRow(review, account, "2026-11-05", "DEBIT", "1.00");
        UUID after = newAccountRow(review, account, "2026-11-06", "DEBIT", "2.00");

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(sameDay)).isFalse();
        assertThat(affectsBalance(after)).isTrue();
    }

    @Test
    void anOlderReconciliationDoesNotMoveTheReferenceBack() {
        UUID account = newAccount("100.00", "2026-11-01T15:00:00Z");
        newSnapshot(account, "edit", "100.00", "2026-11-10T15:00:00Z");
        jdbc.update("UPDATE accounts SET last_reconciliation_date = '2026-11-05T15:00:00Z'::timestamptz WHERE id = ?",
            account);
        UUID review = newAccountImport(account, "REVIEW");
        UUID sameDay = newAccountRow(review, account, "2026-11-10", "DEBIT", "1.00");
        UUID after = newAccountRow(review, account, "2026-11-11", "DEBIT", "2.00");

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(sameDay)).isFalse();
        assertThat(affectsBalance(after)).isTrue();
    }

    @Test
    void aZeroSumMarksTheRowsWithoutASnapshot() {
        UUID account = newAccount("100.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID debit = newAccountRow(review, account, "2026-11-04", "DEBIT", "100.00");
        UUID credit = newAccountRow(review, account, "2026-11-04", "CREDIT", "100.00");

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(debit)).isTrue();
        assertThat(affectsBalance(credit)).isTrue();
        assertThat(balanceOf(account)).isEqualByComparingTo("100.00");
        assertThat(transactionSnapshots(account)).isEmpty();
    }

    @Test
    void aCardPaymentChangesTheBalanceOfTheAccount() {
        UUID account = newAccount("100.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID payment = newAccountRow(review, account, "2026-11-04", "DEBIT", "40.00");
        jdbc.update("UPDATE transactions SET kind = 'CARD_PAYMENT' WHERE id = ?", payment);

        reviewService.complete(userId, review, IP);

        assertThat(affectsBalance(payment)).isTrue();
        assertThat(balanceOf(account)).isEqualByComparingTo("60.00");
    }

    @Test
    void finishingACardImportConfirmsEveryRowIncludingFuturesAndTouchesNoBalance() {
        UUID account = newAccount("100.00", "2026-11-01T15:00:00Z");
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 92, null);
        UUID future = newFuture(review, UUID.randomUUID(), null);

        ImportReviewActionResponse response = reviewService.complete(userId, review, IP);

        assertThat(response.affectedTransactions()).isEqualTo(2);
        assertThat(rowOf(row)).containsEntry("review_status", "CONFIRMED").containsEntry("affects_balance", false)
            .containsEntry("category_id", null);
        assertThat(rowOf(future)).containsEntry("review_status", "CONFIRMED").containsEntry("affects_balance", false);
        assertThat(balanceOf(account)).isEqualByComparingTo("100.00");
        assertThat(transactionSnapshots(account)).isEmpty();
    }

    @Test
    void finishingTwiceAnswersOkAndChangesNothing() {
        UUID account = newAccount("100.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        newAccountRow(review, account, "2026-11-04", "DEBIT", "10.00");
        reviewService.complete(userId, review, IP);

        ImportReviewActionResponse second = reviewService.complete(userId, review, IP);

        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.affectedTransactions()).isZero();
        assertThat(balanceOf(account)).isEqualByComparingTo("90.00");
        assertThat(transactionSnapshots(account)).hasSize(1);
        assertThat(auditOf(TransactionImportReviewService.AUDIT_COMPLETED)).hasSize(1);
    }

    @Test
    void finishingAtTheSameTimeChangesTheBalanceOnce() throws Exception {
        UUID account = newAccount("100.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        newAccountRow(review, account, "2026-11-04", "DEBIT", "10.00");
        CountDownLatch start = new CountDownLatch(1);
        Callable<ImportReviewActionResponse> finish = () -> {
            start.await();
            return reviewService.complete(userId, review, IP);
        };
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<ImportReviewActionResponse>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(executor.submit(finish));
            }
            start.countDown();
            for (Future<ImportReviewActionResponse> call : calls) {
                assertThat(call.get(30, TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(balanceOf(account)).isEqualByComparingTo("90.00");
        assertThat(transactionSnapshots(account)).hasSize(1);
        assertThat(auditOf(TransactionImportReviewService.AUDIT_COMPLETED)).hasSize(1);
    }

    @Test
    void finishingInAnyOtherStatusIs409() {
        for (String status : List.of("DISCARDED", "PROCESSING", "FAILED")) {
            UUID closed = newImport(status);
            UUID row = newRow(closed, 92, null);

            assertThatThrownBy(() -> reviewService.complete(userId, closed, IP)).isInstanceOf(ConflictException.class);

            assertThat(statusOf(closed)).isEqualTo(status);
            assertThat(rowOf(row)).containsEntry("review_status", "PENDING");
        }
        assertThat(auditOf(TransactionImportReviewService.AUDIT_COMPLETED)).isEmpty();
    }

    @Test
    void finishingAnImportOfAnotherUserIs404() {
        UUID review = newImport("REVIEW");

        assertThatThrownBy(() -> reviewService.complete(newUser(), review, IP))
            .isInstanceOf(ResourceNotFoundException.class);

        assertThat(statusOf(review)).isEqualTo("REVIEW");
    }

    @Test
    void aBalanceOutOfRangeIs400AndTheImportStaysInReview() {
        UUID account = newAccount("9999999999999.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID row = newAccountRow(review, account, "2026-11-04", "CREDIT", "1.00");

        assertThatThrownBy(() -> reviewService.complete(userId, review, IP))
            .isInstanceOf(BadRequestException.class)
            .hasMessage(TransactionImportReviewService.BALANCE_OUT_OF_RANGE_MESSAGE);

        assertThat(statusOf(review)).isEqualTo("REVIEW");
        assertThat(rowOf(row)).containsEntry("review_status", "PENDING").containsEntry("affects_balance", false);
        assertThat(balanceOf(account)).isEqualByComparingTo("9999999999999.00");
        assertThat(transactionSnapshots(account)).isEmpty();
        assertThat(auditOf(TransactionImportReviewService.AUDIT_COMPLETED)).isEmpty();
    }

    @Test
    void aRemovedAccountIs409AndNothingChanges() {
        UUID account = newAccount("100.00", "2026-11-03T15:00:00Z");
        UUID review = newAccountImport(account, "REVIEW");
        UUID row = newAccountRow(review, account, "2026-11-04", "DEBIT", "10.00");
        jdbc.update("UPDATE accounts SET deleted_at = NOW() WHERE id = ?", account);

        assertThatThrownBy(() -> reviewService.complete(userId, review, IP))
            .isInstanceOf(ConflictException.class).hasMessageContaining("restaure ou descarte a importação");

        assertThat(statusOf(review)).isEqualTo("REVIEW");
        assertThat(rowOf(row)).containsEntry("review_status", "PENDING");
        assertThat(balanceOf(account)).isEqualByComparingTo("100.00");
    }

    @Test
    void aRemovedCardIs409AndNothingChanges() {
        UUID review = newImport("REVIEW");
        UUID row = newRow(review, 92, null);
        jdbc.update("UPDATE credit_cards SET deleted_at = NOW() WHERE id = ?", cardId);

        assertThatThrownBy(() -> reviewService.complete(userId, review, IP))
            .isInstanceOf(ConflictException.class).hasMessageContaining("restaure ou descarte a importação");

        assertThat(statusOf(review)).isEqualTo("REVIEW");
        assertThat(rowOf(row)).containsEntry("review_status", "PENDING");
    }

    @Test
    void anAlreadyCompletedImportOfARemovedTargetStillAnswersOk() {
        UUID account = newAccount("100.00", "2026-11-03T15:00:00Z");
        UUID done = newAccountImport(account, "COMPLETED");
        jdbc.update("UPDATE accounts SET deleted_at = NOW() WHERE id = ?", account);

        assertThat(reviewService.complete(userId, done, IP).status()).isEqualTo("COMPLETED");
    }

    @Test
    void discardingTheCreatorAfterTheConfirmerFinishedLeavesTheRowConfirmed() {
        UUID creator = newImport("REVIEW");
        UUID confirmer = newImport("REVIEW");
        UUID confirmed = newRow(creator, 92, null);
        jdbc.update("UPDATE transactions SET confirmed_by_import_id = ? WHERE id = ?", confirmer, confirmed);

        reviewService.complete(userId, confirmer, IP);
        reviewService.discard(userId, creator, IP);

        assertThat(rowOf(confirmed)).containsEntry("import_id", confirmer).containsEntry("review_status", "CONFIRMED");
    }

    // Story 5.11

    private UUID rowWithDescription(UUID importId, String description) {
        UUID id = newRow(importId, 60, null);
        jdbc.update("UPDATE transactions SET description = ? WHERE id = ?", description, id);
        return id;
    }

    private void predicted(UUID transactionId, UUID categoryId) {
        jdbc.update("INSERT INTO ai_classification_history (user_id, transaction_id, predicted_category_id, confidence) "
            + "VALUES (?, ?, ?, 60)", userId, transactionId, categoryId);
    }

    private Map<String, Object> historyOf(UUID transactionId) {
        return jdbc.queryForMap("SELECT * FROM ai_classification_history WHERE transaction_id = ?", transactionId);
    }

    private List<Map<String, Object>> patterns() {
        return jdbc.queryForList("SELECT * FROM user_classification_patterns WHERE user_id = ?", userId);
    }

    @Test
    void aCorrectionIsRecordedAndSavingAgainRecomputesItAndTheCounter() {
        UUID review = newImport("REVIEW");
        UUID group = newCategory("Alimentação", null);
        UUID mercado = newCategory("Mercado", group);
        UUID restaurante = newCategory("Restaurante", group);
        UUID row = rowWithDescription(review, "UBER *TRIP 12/03 PARC 02/05");
        predicted(row, mercado);

        reviewService.updateCategory(userId, review, edit(restaurante, row), IP);

        assertThat(historyOf(row)).containsEntry("was_corrected", true).containsEntry("corrected_category_id", restaurante);
        assertThat(patterns()).singleElement().satisfies(p -> {
            assertThat(p.get("description_pattern")).isEqualTo("uber trip");
            assertThat(p.get("preferred_category_id")).isEqualTo(restaurante);
            assertThat(p.get("usage_count")).isEqualTo(1);
        });

        // back to the suggestion: no correction, and the pattern starts again with the other category
        reviewService.updateCategory(userId, review, edit(mercado, row), IP);
        assertThat(historyOf(row)).containsEntry("was_corrected", false).containsEntry("corrected_category_id", null);
        assertThat(patterns()).singleElement().satisfies(p -> {
            assertThat(p.get("preferred_category_id")).isEqualTo(mercado);
            assertThat(p.get("usage_count")).isEqualTo(1);
        });

        // the same category again (decision Q2)
        reviewService.updateCategory(userId, review, edit(mercado, row), IP);
        assertThat(patterns()).singleElement().satisfies(p -> assertThat(p.get("usage_count")).isEqualTo(2));
    }

    @Test
    void aRowWithoutHistoryTeachesAndEqualDescriptionsMakeOnePattern() {
        UUID review = newImport("REVIEW");
        UUID transporte = newCategory("Transporte", newCategory("Mobilidade", null));
        UUID first = rowWithDescription(review, "UBER TRIP 01/11");
        UUID second = rowWithDescription(review, "Uber Trip");
        UUID third = rowWithDescription(review, "uber *trip");
        UUID number = rowWithDescription(review, "123456");

        reviewService.updateCategory(userId, review, edit(transporte, first, second, third, number), IP);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_classification_history WHERE user_id = ?", Long.class,
            userId)).isZero();
        assertThat(patterns()).singleElement().satisfies(p -> {
            assertThat(p.get("description_pattern")).isEqualTo("uber trip");
            assertThat(p.get("preferred_category_id")).isEqualTo(transporte);
            assertThat(p.get("usage_count")).isEqualTo(1);
        });
    }

    @Test
    void onlyGenericBankingTermsTeachNothingButANameMakesThemPartOfThePattern() {
        UUID review = newImport("REVIEW");
        UUID transporte = newCategory("Transporte", newCategory("Mobilidade", null));

        reviewService.updateCategory(userId, review, edit(transporte, rowWithDescription(review, "PIX 98765"),
            rowWithDescription(review, "PAGAMENTO BOLETO 3421"), rowWithDescription(review, "PIX TRANSF JOAO SILVA")), IP);

        assertThat(patterns()).singleElement()
            .satisfies(p -> assertThat(p.get("description_pattern")).isEqualTo("pix transf joao silva"));
    }

    @Test
    void theUsablePatternsAreTheUsersWithAnActiveChildCategoryAndThePurgeTakesThemAll() {
        UUID review = newImport("REVIEW");
        UUID group = newCategory("Alimentação", null);
        UUID mercado = newCategory("Mercado", group);
        UUID removed = newCategory("Antiga", group);
        UUID turnedGroup = newCategory("Virou grupo", group);
        reviewService.updateCategory(userId, review, edit(mercado, rowWithDescription(review, "PADARIA")), IP);
        reviewService.updateCategory(userId, review, edit(removed, rowWithDescription(review, "UBER")), IP);
        reviewService.updateCategory(userId, review, edit(turnedGroup, rowWithDescription(review, "IFOOD")), IP);
        jdbc.update("UPDATE categories SET deleted_at = now() WHERE id = ?", removed);
        jdbc.update("UPDATE categories SET parent_category_id = NULL WHERE id = ?", turnedGroup);
        UUID other = newUser();

        assertThat(patternRepository.findUsableByUserId(other)).isEmpty();
        assertThat(patternRepository.findUsableByUserId(userId)).singleElement()
            .satisfies(p -> assertThat(p.getDescriptionPattern()).isEqualTo("padaria"));
        Integer purged = transactionTemplate.execute(status -> patternRepository.deleteByUserId(userId));
        assertThat(purged).isEqualTo(3);
        assertThat(patternRepository.findByUserIdOrdered(userId)).isEmpty();
    }
}
