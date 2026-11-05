package com.porquinho.repository;

import com.porquinho.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository for Transaction (Story 5.4). */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Every row of the user, removed ones included, oldest first (LGPD export). */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.createdAt ASC, t.id ASC")
    List<Transaction> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's transactions (LGPD account purge, Story 5.4). Must run BEFORE deleting the imports,
     * accounts, cards, categories and the user row (FKs to all of them).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM transactions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
