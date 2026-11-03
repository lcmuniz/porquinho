package com.porquinho.repository;

import com.porquinho.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Account (Story 3.2). Every lookup is scoped by user_id; "active" means deleted_at IS NULL.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** The user's active accounts, oldest first (id breaks ties for rows created in the same instant). */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.deletedAt IS NULL ORDER BY a.createdAt ASC, a.id ASC")
    List<Account> findActiveByUserId(@Param("userId") UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    /** The user's removed rows, most recently removed first (Story 4.4). */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.deletedAt IS NOT NULL ORDER BY a.deletedAt DESC, a.id ASC")
    List<Account> findDeletedByUserId(@Param("userId") UUID userId);

    /**
     * The user's active row, row-locked (Story 4.4): an edit, a removal and a restore of the same row are serialized,
     * so an edit loaded before a concurrent removal cannot write deleted_at/deleted_by back to NULL.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.userId = :userId AND a.deletedAt IS NULL")
    Optional<Account> findActiveByIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /** The user's removed row, row-locked (Story 4.4): two concurrent restores cannot both restore and audit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.userId = :userId AND a.deletedAt IS NOT NULL")
    Optional<Account> findDeletedByIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * The user's active accounts, row-locked (Story 4.4): two concurrent removals cannot both see two active
     * accounts and leave the user with none.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.deletedAt IS NULL ORDER BY a.id ASC")
    List<Account> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    /** Every row of the user, removed ones included (LGPD export). */
    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Hard-delete the user's accounts (LGPD account purge, Story 3.2).
     * Must run BEFORE deleting the user row (FK accounts.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM accounts WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
