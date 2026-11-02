package com.porquinho.repository;

import com.porquinho.entity.OnboardingProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OnboardingProgress (Story 3.1).
 */
@Repository
public interface OnboardingProgressRepository extends JpaRepository<OnboardingProgress, UUID> {

    Optional<OnboardingProgress> findByUserId(UUID userId);

    /** Row lock so two concurrent "complete tutorial" calls do not both advance and audit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OnboardingProgress o WHERE o.userId = :userId")
    Optional<OnboardingProgress> findByUserIdForUpdate(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's onboarding progress (LGPD account purge, Story 3.1).
     * Must run BEFORE deleting the user row (FK onboarding_progress.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM onboarding_progress WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
