package com.porquinho.repository;

import com.porquinho.entity.Reconciliation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Reconciliation (Story 4.6). Every lookup is scoped by user_id.
 */
@Repository
public interface ReconciliationRepository extends JpaRepository<Reconciliation, UUID> {

    /**
     * The account's reconciliations, most recent first (id breaks ties), limited by {@code page}. Served by
     * idx_reconciliation_history_account_date.
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.accountId = :accountId AND r.userId = :userId "
        + "ORDER BY r.reconciledAt DESC, r.id DESC")
    List<Reconciliation> findRecentByAccountId(@Param("accountId") UUID accountId, @Param("userId") UUID userId,
                                               Pageable page);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT r FROM Reconciliation r WHERE r.userId = :userId ORDER BY r.reconciledAt ASC, r.createdAt ASC, r.id ASC")
    List<Reconciliation> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's reconciliations (LGPD account purge, Story 4.6).
     * Must run BEFORE deleting the user row (FK user_id -> users.id). The FK to accounts cascades, but the purge
     * deletes the rows explicitly anyway, before the accounts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM reconciliation_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
