package com.porquinho.repository;

import com.porquinho.entity.AccountBalanceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AccountBalanceHistory (Story 4.3). Written by AccountService; read by the LGPD export
 * and by the balance chart (Story 4.7, index account_id + snapshot_date).
 */
@Repository
public interface AccountBalanceHistoryRepository extends JpaRepository<AccountBalanceHistory, UUID> {

    /** Every snapshot of the user, oldest first (LGPD export; id breaks ties). */
    @Query("SELECT h FROM AccountBalanceHistory h WHERE h.userId = :userId ORDER BY h.snapshotDate ASC, h.createdAt ASC, h.id ASC")
    List<AccountBalanceHistory> findByUserIdOrdered(@Param("userId") UUID userId);

    /** Every snapshot of the account, oldest first (Story 4.7, range "all"); created_at then id break ties. */
    @Query("SELECT h FROM AccountBalanceHistory h WHERE h.accountId = :accountId AND h.userId = :userId "
        + "ORDER BY h.snapshotDate ASC, h.createdAt ASC, h.id ASC")
    List<AccountBalanceHistory> findByAccount(@Param("accountId") UUID accountId, @Param("userId") UUID userId);

    /** The account's snapshots from {@code from} on, oldest first (Story 4.7). */
    @Query("SELECT h FROM AccountBalanceHistory h WHERE h.accountId = :accountId AND h.userId = :userId "
        + "AND h.snapshotDate >= :from ORDER BY h.snapshotDate ASC, h.createdAt ASC, h.id ASC")
    List<AccountBalanceHistory> findByAccountSince(@Param("accountId") UUID accountId, @Param("userId") UUID userId,
                                                   @Param("from") OffsetDateTime from);

    /** The account's snapshots before {@code from}, newest first; called with one row to get the last known balance. */
    @Query("SELECT h FROM AccountBalanceHistory h WHERE h.accountId = :accountId AND h.userId = :userId "
        + "AND h.snapshotDate < :from ORDER BY h.snapshotDate DESC, h.createdAt DESC, h.id DESC")
    List<AccountBalanceHistory> findByAccountBefore(@Param("accountId") UUID accountId, @Param("userId") UUID userId,
                                                    @Param("from") OffsetDateTime from, Pageable pageable);

    /**
     * Hard-delete the user's snapshots (LGPD account purge, Story 4.3).
     * Must run BEFORE deleting the user row (FK user_id -> users.id). The FK to accounts cascades, but the
     * purge deletes the snapshots explicitly anyway, before the accounts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM account_balance_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
