package com.porquinho.repository;

import com.porquinho.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<Account> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

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
