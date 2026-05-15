package com.porquinho.repository;

import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AccountDeletion entity (Story 1.7 - LGPD account deletion).
 */
@Repository
public interface AccountDeletionRepository extends JpaRepository<AccountDeletion, UUID> {

    /**
     * Idempotency check: is there an active (PENDING_PURGE) deletion for this user?
     */
    Optional<AccountDeletion> findByUserIdAndStatus(UUID userId, DeletionStatus status);

    /**
     * Used by the daily purge job to find deletions ready to be hard-deleted.
     */
    List<AccountDeletion> findAllByStatusAndScheduledPurgeAtBefore(DeletionStatus status, OffsetDateTime threshold);
}
