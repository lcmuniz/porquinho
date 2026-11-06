package com.porquinho.service;

import com.porquinho.dto.ImportReviewActionResponse;
import com.porquinho.dto.ImportTransactionResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Story 5.9: the review of an import against the real schema (PostgreSQL via Testcontainers, Flyway through V29). */
@SpringBootTest
@ActiveProfiles("test")
class TransactionImportReviewServiceTest {

    private static final String IP = "127.0.0.1";

    @Autowired
    private TransactionImportReviewService reviewService;
    @Autowired
    private JdbcTemplate jdbc;
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
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", id);
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
}
