package com.porquinho.repository;

import com.porquinho.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Repository for Transaction (Story 5.4). */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Every row of the user, removed ones included, oldest first (LGPD export). */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.createdAt ASC, t.id ASC")
    List<Transaction> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * The user's active transactions of account {@code accountId} dated within [{@code from}, {@code to}], any review
     * status, in a fixed order: the candidates for the duplicate check of an import (Story 5.5). Served by idx_transactions_account_date.
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.accountId = :accountId "
        + "AND t.deletedAt IS NULL AND t.transactionDate BETWEEN :from AND :to "
        + "ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC")
    List<Transaction> findActiveByAccountBetween(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Same as {@link #findActiveByAccountBetween} for credit card {@code creditCardId} (Story 5.5). */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.creditCardId = :creditCardId "
        + "AND t.deletedAt IS NULL AND t.transactionDate BETWEEN :from AND :to "
        + "ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC")
    List<Transaction> findActiveByCreditCardBetween(@Param("userId") UUID userId,
                                                    @Param("creditCardId") UUID creditCardId,
                                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * The user's active installments (imported and future) of credit card {@code creditCardId}, oldest first: the
     * groups a card import can join or confirm (Story 5.7).
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.creditCardId = :creditCardId "
        + "AND t.installment = true AND t.deletedAt IS NULL ORDER BY t.createdAt ASC, t.id ASC")
    List<Transaction> findActiveInstallmentsByCreditCard(@Param("userId") UUID userId,
                                                         @Param("creditCardId") UUID creditCardId);

    /**
     * The rows of import {@code importId} that go to the AI (Story 5.8): neither card payments (decision D8) nor future
     * installments, which inherit (decision 1A). By date, so that the batches are always the same.
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.importId = :importId "
        + "AND t.kind = com.porquinho.entity.Transaction.Kind.REGULAR AND t.futureInstallment = false "
        + "AND t.deletedAt IS NULL ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC")
    List<Transaction> findClassifiableByImport(@Param("userId") UUID userId, @Param("importId") UUID importId);

    /**
     * Writes the classification of one row (Story 5.8). Only these columns: an import running at the same time may be
     * changing the same rows, and saving the entity would write all of them back.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.categoryId = :categoryId, t.classificationConfidence = :confidence, "
        + "t.classificationSource = com.porquinho.entity.Transaction.ClassificationSource.AI, t.updatedAt = :now "
        + "WHERE t.id = :id")
    int applyAiClassification(@Param("id") UUID id, @Param("categoryId") UUID categoryId,
                              @Param("confidence") int confidence, @Param("now") OffsetDateTime now);

    /**
     * Story 5.8, decision 1A: the future installments that import {@code importId} created for a purchase inherit the
     * classification of its installment in the file. One already confirmed is no longer future and stays as it is.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.categoryId = :categoryId, t.classificationConfidence = :confidence, "
        + "t.classificationSource = com.porquinho.entity.Transaction.ClassificationSource.AI, t.updatedAt = :now "
        + "WHERE t.importId = :importId AND t.installmentGroupId = :groupId AND t.futureInstallment = true "
        + "AND t.deletedAt IS NULL")
    int applyAiClassificationToFutures(@Param("importId") UUID importId, @Param("groupId") UUID groupId,
                                       @Param("categoryId") UUID categoryId, @Param("confidence") int confidence,
                                       @Param("now") OffsetDateTime now);

    /**
     * The rows of import {@code importId} shown in the review (Story 5.9, decision 2A): card payments included, future
     * installments left out, by date.
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.importId = :importId "
        + "AND t.futureInstallment = false AND t.deletedAt IS NULL "
        + "ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC")
    List<Transaction> findForReviewByImport(@Param("userId") UUID userId, @Param("importId") UUID importId);

    /**
     * Story 5.9, decision 1A: approves the pending rows the review shows as high confidence. A row without category is
     * "Não classificado", whatever its confidence. Rows already approved stay out, so a second call changes nothing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.APPROVED, "
        + "t.updatedAt = :now WHERE t.userId = :userId AND t.importId = :importId "
        + "AND t.kind = com.porquinho.entity.Transaction.Kind.REGULAR AND t.futureInstallment = false "
        + "AND t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.PENDING "
        + "AND t.classificationConfidence >= :minConfidence AND t.categoryId IS NOT NULL AND t.deletedAt IS NULL")
    int approveHighConfidence(@Param("userId") UUID userId, @Param("importId") UUID importId,
                              @Param("minConfidence") int minConfidence, @Param("now") OffsetDateTime now);

    /**
     * Story 5.9, decision 3A: the import {@code importId} is being discarded, so the installments it created and another
     * import confirmed go to that import, CONFIRMED when it is already COMPLETED. Native SQL: {@code import_id} is not
     * updatable in the entity. Must run before {@link #softDeleteByImport}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transactions SET import_id = confirmed_by_import_id, confirmed_by_import_id = NULL, "
        + "review_status = CASE WHEN (SELECT i.status FROM transaction_imports i "
        + "WHERE i.id = transactions.confirmed_by_import_id) = 'COMPLETED' THEN 'CONFIRMED' ELSE review_status END, "
        + "updated_at = :now WHERE user_id = :userId AND import_id = :importId "
        + "AND confirmed_by_import_id IS NOT NULL AND deleted_at IS NULL", nativeQuery = true)
    int transferConfirmedInstallments(@Param("userId") UUID userId, @Param("importId") UUID importId,
                                      @Param("now") OffsetDateTime now);

    /**
     * Story 5.9, decision 3A: the import {@code importId} is being discarded, so the installments it confirmed for other
     * imports are future again. Description, date and amount stay as the discarded file wrote them. An approval goes
     * back to PENDING: nobody reviewed that data as it will come in the next confirmation. CONFIRMED stays.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.futureInstallment = true, t.externalId = NULL, t.confirmedByImportId = NULL, "
        + "t.reviewStatus = CASE WHEN t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.APPROVED "
        + "THEN com.porquinho.entity.Transaction.ReviewStatus.PENDING ELSE t.reviewStatus END, "
        + "t.updatedAt = :now WHERE t.userId = :userId AND t.confirmedByImportId = :importId AND t.deletedAt IS NULL")
    int revertConfirmedInstallments(@Param("userId") UUID userId, @Param("importId") UUID importId,
                                    @Param("now") OffsetDateTime now);

    /** Soft delete of every active row of import {@code importId}, future installments included (Story 5.9). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.deletedAt = :now, t.updatedAt = :now "
        + "WHERE t.userId = :userId AND t.importId = :importId AND t.deletedAt IS NULL")
    int softDeleteByImport(@Param("userId") UUID userId, @Param("importId") UUID importId,
                           @Param("now") OffsetDateTime now);

    /**
     * Hard-delete the user's transactions (LGPD account purge, Story 5.4). Must run BEFORE deleting the imports,
     * accounts, cards, categories and the user row (FKs to all of them).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM transactions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
