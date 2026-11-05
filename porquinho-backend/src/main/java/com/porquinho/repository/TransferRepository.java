package com.porquinho.repository;

import com.porquinho.entity.Transfer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Transfer (Story 4.5). Every lookup is scoped by user_id; "active" means deleted_at IS NULL.
 */
@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    /**
     * The user's active transfers, most recent first (id breaks ties for rows created in the same instant), limited
     * by {@code page}. Served by idx_transfers_user_date.
     */
    @Query("SELECT t FROM Transfer t WHERE t.userId = :userId AND t.deletedAt IS NULL "
        + "ORDER BY t.transferDate DESC, t.createdAt DESC, t.id DESC")
    List<Transfer> findActiveByUserId(@Param("userId") UUID userId, Pageable page);

    /**
     * The user's active transfers from or to account {@code accountId} dated within [{@code from}, {@code to}]: the
     * transfers an import into that account may already contain (Story 5.5, decision D4).
     */
    @Query("SELECT t FROM Transfer t WHERE t.userId = :userId AND t.deletedAt IS NULL "
        + "AND (t.fromAccountId = :accountId OR t.toAccountId = :accountId) "
        + "AND t.transferDate BETWEEN :from AND :to "
        + "ORDER BY t.transferDate ASC, t.createdAt ASC, t.id ASC")
    List<Transfer> findActiveByAccountBetween(@Param("userId") UUID userId, @Param("accountId") UUID accountId,
                                              @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every row of the user, removed ones included, oldest first (LGPD export). */
    @Query("SELECT t FROM Transfer t WHERE t.userId = :userId ORDER BY t.createdAt ASC, t.id ASC")
    List<Transfer> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's transfers (LGPD account purge, Story 4.5).
     * Must run BEFORE deleting the accounts and the user row (FKs to accounts.id and users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM transfers WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
