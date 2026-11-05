package com.porquinho.repository;

import com.porquinho.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
     * Hard-delete the user's transactions (LGPD account purge, Story 5.4). Must run BEFORE deleting the imports,
     * accounts, cards, categories and the user row (FKs to all of them).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM transactions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
