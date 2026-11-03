package com.porquinho.repository;

import com.porquinho.entity.AccountBalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for AccountBalanceHistory (Story 4.3). Written by AccountService; read by the LGPD export
 * (and, from Story 4.7, by the balance chart).
 */
@Repository
public interface AccountBalanceHistoryRepository extends JpaRepository<AccountBalanceHistory, UUID> {

    /** Every snapshot of the user, oldest first (LGPD export; id breaks ties). */
    @Query("SELECT h FROM AccountBalanceHistory h WHERE h.userId = :userId ORDER BY h.snapshotDate ASC, h.createdAt ASC, h.id ASC")
    List<AccountBalanceHistory> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's snapshots (LGPD account purge, Story 4.3).
     * Must run BEFORE deleting the user row (FK user_id -> users.id). The FK to accounts cascades, but the
     * purge deletes the snapshots explicitly anyway, before the accounts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM account_balance_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
