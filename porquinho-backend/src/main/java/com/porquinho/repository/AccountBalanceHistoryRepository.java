package com.porquinho.repository;

import com.porquinho.entity.AccountBalanceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AccountBalanceHistory (Story 4.3). Written only by BalanceHistoryRecorder; read by the LGPD export
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

    /** Every snapshot of the user from {@code from} on, oldest first (Story 7.4, wealth curve). */
    @Query("SELECT h FROM AccountBalanceHistory h WHERE h.userId = :userId AND h.snapshotDate >= :from "
        + "ORDER BY h.snapshotDate ASC, h.createdAt ASC, h.id ASC")
    List<AccountBalanceHistory> findByUserSince(@Param("userId") UUID userId, @Param("from") OffsetDateTime from);

    /** The last snapshot before {@code from} of each of the user's accounts, removed ones included (Story 7.4). */
    @Query(value = "SELECT DISTINCT ON (account_id) * FROM account_balance_history WHERE user_id = :userId "
        + "AND snapshot_date < :from ORDER BY account_id, snapshot_date DESC, created_at DESC, id DESC",
        nativeQuery = true)
    List<AccountBalanceHistory> findLastPerAccountBefore(@Param("userId") UUID userId,
                                                         @Param("from") OffsetDateTime from);

    /** When the account last got a snapshot of one of {@code events}: part of the reference date of D3 (Story 5.10). */
    @Query("SELECT MAX(h.snapshotDate) FROM AccountBalanceHistory h WHERE h.accountId = :accountId "
        + "AND h.userId = :userId AND h.createdByEvent IN :events")
    Optional<OffsetDateTime> findLastSnapshotDate(@Param("accountId") UUID accountId, @Param("userId") UUID userId,
                                                  @Param("events") Collection<String> events);

    /**
     * Hard-delete the user's snapshots (LGPD account purge, Story 4.3).
     * Must run BEFORE deleting the user row (FK user_id -> users.id). The FK to accounts cascades, but the
     * purge deletes the snapshots explicitly anyway, before the accounts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM account_balance_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
