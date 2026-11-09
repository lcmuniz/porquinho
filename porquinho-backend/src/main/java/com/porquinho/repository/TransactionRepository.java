package com.porquinho.repository;

import com.porquinho.entity.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for Transaction (Story 5.4). */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Every row of the user, removed ones included, oldest first (LGPD export). */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.createdAt ASC, t.id ASC")
    List<Transaction> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * The user's transactions of account {@code accountId} dated within [{@code from}, {@code to}], in a fixed order: the
     * candidates for the duplicate check of an import (Story 5.5). Active rows of any review status, plus confirmed rows
     * the user removed (retro Epic 5, X1), so that a removed row does not come back with an overlapping statement. A
     * discarded import never has confirmed rows, so its rows stay out. Served by idx_transactions_account_date.
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.accountId = :accountId "
        + "AND (t.deletedAt IS NULL OR t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED) "
        + "AND t.transactionDate BETWEEN :from AND :to ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC")
    List<Transaction> findDuplicateCandidatesByAccountBetween(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Same as {@link #findDuplicateCandidatesByAccountBetween} for credit card {@code creditCardId} (Story 5.5). */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.creditCardId = :creditCardId "
        + "AND (t.deletedAt IS NULL OR t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED) "
        + "AND t.transactionDate BETWEEN :from AND :to ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC")
    List<Transaction> findDuplicateCandidatesByCreditCardBetween(@Param("userId") UUID userId,
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
     * Retro Epic 5, X2: the balance of account {@code accountId} was just set by hand (edit or reconciliation), so the
     * rows that were in it are absorbed: removing or editing one later must not change the balance the user set.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.affectsBalance = false, t.updatedAt = :now WHERE t.userId = :userId "
        + "AND t.accountId = :accountId AND t.affectsBalance = true")
    int absorbIntoBalance(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                          @Param("now") OffsetDateTime now);

    /**
     * Writes the classification of one row, by the AI (Story 5.8) or by a pattern of the user (Story 5.11). Only these
     * columns: an import running at the same time may be changing the same rows, and saving the entity would write all
     * of them back.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.categoryId = :categoryId, t.classificationConfidence = :confidence, "
        + "t.classificationSource = :source, t.updatedAt = :now WHERE t.id = :id")
    int applyClassification(@Param("id") UUID id, @Param("categoryId") UUID categoryId,
                            @Param("confidence") int confidence, @Param("source") Transaction.ClassificationSource source,
                            @Param("now") OffsetDateTime now);

    /**
     * Story 5.8, decision 1A: the future installments that import {@code importId} created for a purchase inherit the
     * classification of its installment in the file. One already confirmed is no longer future and stays as it is.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.categoryId = :categoryId, t.classificationConfidence = :confidence, "
        + "t.classificationSource = :source, t.updatedAt = :now "
        + "WHERE t.importId = :importId AND t.installmentGroupId = :groupId AND t.futureInstallment = true "
        + "AND t.deletedAt IS NULL")
    int applyClassificationToFutures(@Param("importId") UUID importId, @Param("groupId") UUID groupId,
                                     @Param("categoryId") UUID categoryId, @Param("confidence") int confidence,
                                     @Param("source") Transaction.ClassificationSource source,
                                     @Param("now") OffsetDateTime now);

    /** The descriptions of the user's transactions {@code ids} (Story 5.11: what the user's choice teaches). */
    @Query("SELECT t.description FROM Transaction t WHERE t.userId = :userId AND t.id IN :ids")
    List<String> findDescriptions(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

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

    /** How many of {@code ids} are rows the user can edit in the review of the import (Story 5.10). */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.importId = :importId AND t.id IN :ids "
        + "AND t.kind = com.porquinho.entity.Transaction.Kind.REGULAR AND t.futureInstallment = false "
        + "AND t.deletedAt IS NULL")
    long countEditable(@Param("userId") UUID userId, @Param("importId") UUID importId,
                       @Param("ids") Collection<UUID> ids);

    /** Writes the category the user chose on rows of the review (Story 5.10): reviewed by the user, so APPROVED. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.categoryId = :categoryId, t.classificationConfidence = :confidence, "
        + "t.classificationSource = com.porquinho.entity.Transaction.ClassificationSource.USER, "
        + "t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.APPROVED, t.updatedAt = :now "
        + "WHERE t.userId = :userId AND t.importId = :importId AND t.id IN :ids "
        + "AND t.kind = com.porquinho.entity.Transaction.Kind.REGULAR AND t.futureInstallment = false "
        + "AND t.deletedAt IS NULL")
    int applyUserCategory(@Param("userId") UUID userId, @Param("importId") UUID importId,
                          @Param("ids") Collection<UUID> ids, @Param("categoryId") UUID categoryId,
                          @Param("confidence") int confidence, @Param("now") OffsetDateTime now);

    /**
     * Story 5.8, decision 1A, for the choice of the user: the future installments of import {@code importId} follow the
     * rows {@code ids} of their purchases. They stay out of the review, so their review status does not change.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.categoryId = :categoryId, t.classificationConfidence = :confidence, "
        + "t.classificationSource = com.porquinho.entity.Transaction.ClassificationSource.USER, t.updatedAt = :now "
        + "WHERE t.userId = :userId AND t.importId = :importId AND t.futureInstallment = true "
        + "AND t.deletedAt IS NULL AND t.installmentGroupId IN (SELECT r.installmentGroupId FROM Transaction r "
        + "WHERE r.userId = :userId AND r.id IN :ids AND r.installmentGroupId IS NOT NULL)")
    int applyUserCategoryToFutures(@Param("userId") UUID userId, @Param("importId") UUID importId,
                                   @Param("ids") Collection<UUID> ids, @Param("categoryId") UUID categoryId,
                                   @Param("confidence") int confidence, @Param("now") OffsetDateTime now);

    /**
     * Decision D3 (Story 5.10): the rows of import {@code importId} that may enter the balance when it is finished, its
     * active rows other than future installments. Which of them do is decided by {@code TransactionCore#inBalance}.
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.importId = :importId "
        + "AND t.futureInstallment = false AND t.deletedAt IS NULL")
    List<Transaction> findBalanceCandidates(@Param("userId") UUID userId, @Param("importId") UUID importId);

    /** Decision D3 (Story 5.10): marks the rows that entered the balance when their import was finished. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.affectsBalance = true, t.updatedAt = :now WHERE t.userId = :userId "
        + "AND t.id IN :ids")
    int markInBalance(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids,
                           @Param("now") OffsetDateTime now);

    /** The import is finished (Story 5.10): every active row is CONFIRMED, future installments included (5.9, 2A). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED, "
        + "t.updatedAt = :now WHERE t.userId = :userId AND t.importId = :importId AND t.deletedAt IS NULL")
    int confirmByImport(@Param("userId") UUID userId, @Param("importId") UUID importId,
                        @Param("now") OffsetDateTime now);

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
     * The user's confirmed transactions dated within [{@code from}, {@code to}], newest first, future installments left
     * out (Story 5.12, decision Q1).
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId "
        + "AND t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED AND t.futureInstallment = false "
        + "AND t.deletedAt IS NULL AND t.transactionDate BETWEEN :from AND :to "
        + "ORDER BY t.transactionDate DESC, t.createdAt DESC, t.id DESC")
    List<Transaction> findConfirmedBetween(@Param("userId") UUID userId, @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /** The user's confirmed transaction the user can edit or remove (Story 5.12), row-locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.userId = :userId "
        + "AND t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED AND t.futureInstallment = false "
        + "AND t.deletedAt IS NULL")
    Optional<Transaction> findEditableForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Where the user's editable transaction is: account or card id, null if there is no such row (Story 5.12). */
    @Query("SELECT COALESCE(t.creditCardId, t.accountId) FROM Transaction t WHERE t.id = :id AND t.userId = :userId "
        + "AND t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED AND t.futureInstallment = false "
        + "AND t.deletedAt IS NULL")
    Optional<UUID> findEditableTarget(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Each of the user's groups {@code groupIds}: [group id, active future installments, active confirmed installments]
     * (Story 5.12).
     */
    @Query("SELECT t.installmentGroupId, "
        + "SUM(CASE WHEN t.futureInstallment = true THEN 1 ELSE 0 END), "
        + "SUM(CASE WHEN t.futureInstallment = false "
        + "AND t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED THEN 1 ELSE 0 END) "
        + "FROM Transaction t WHERE t.userId = :userId AND t.installmentGroupId IN :groupIds AND t.deletedAt IS NULL "
        + "GROUP BY t.installmentGroupId")
    List<Object[]> countActiveByGroup(@Param("userId") UUID userId, @Param("groupIds") Collection<UUID> groupIds);

    /** The active confirmed installments of the user's group {@code groupId} other than {@code id} (Story 5.12, Q3). */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.installmentGroupId = :groupId "
        + "AND t.id <> :id AND t.futureInstallment = false AND t.deletedAt IS NULL "
        + "AND t.reviewStatus = com.porquinho.entity.Transaction.ReviewStatus.CONFIRMED")
    long countOtherConfirmedInGroup(@Param("userId") UUID userId, @Param("groupId") UUID groupId,
                                    @Param("id") UUID id);

    /** Soft delete of the active future installments of the user's group {@code groupId} (Story 5.12). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.deletedAt = :now, t.updatedAt = :now WHERE t.userId = :userId "
        + "AND t.installmentGroupId = :groupId AND t.futureInstallment = true AND t.deletedAt IS NULL")
    int softDeleteFutures(@Param("userId") UUID userId, @Param("groupId") UUID groupId,
                          @Param("now") OffsetDateTime now);

    /**
     * Story 5.12, decision Q2: the active rows of the user's group {@code groupId} remember that its futures were
     * removed, so the next import of the card creates none.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.installmentFuturesRemovedAt = :now, t.updatedAt = :now WHERE t.userId = :userId "
        + "AND t.installmentGroupId = :groupId AND t.deletedAt IS NULL")
    int markFuturesRemoved(@Param("userId") UUID userId, @Param("groupId") UUID groupId,
                           @Param("now") OffsetDateTime now);

    /**
     * Hard-delete the user's transactions (LGPD account purge, Story 5.4). Must run BEFORE deleting the imports,
     * accounts, cards, categories and the user row (FKs to all of them).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM transactions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
