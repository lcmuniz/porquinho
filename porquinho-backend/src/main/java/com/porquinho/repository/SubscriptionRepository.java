package com.porquinho.repository;

import com.porquinho.entity.Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Subscription entity (Story 2.1).
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * Row lock for webhook processing (Story 2.2): Stripe may deliver the same event twice
     * concurrently; the lock serializes them so the idempotency check sees the first write.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId")
    Optional<Subscription> findByUserIdForUpdate(@Param("userId") UUID userId);

    /**
     * Row lock by Stripe subscription id (Story 2.4, customer.subscription.deleted webhook).
     * stripe_subscription_id is UNIQUE (V10), so at most one row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.stripeSubscriptionId = :stripeSubscriptionId")
    Optional<Subscription> findByStripeSubscriptionIdForUpdate(@Param("stripeSubscriptionId") String stripeSubscriptionId);

    /**
     * Candidates for the daily trial reminder job (Story 2.3): running trials with a reminder possibly due,
     * i.e. 7d pending and ending by sevenDayUntil, or 1d pending and ending by oneDayUntil. Narrow on purpose:
     * rows already reminded (7d) are not re-locked daily until the 1d window. The exact day rules
     * (Sao Paulo calendar days) live in TrialReminderService.
     */
    @Query("SELECT s FROM Subscription s WHERE s.planType = com.porquinho.entity.Subscription.PlanType.TRIAL "
        + "AND s.status = com.porquinho.entity.Subscription.Status.ACTIVE "
        + "AND s.trialEndDate > :now "
        + "AND ((s.trialReminder7dSentAt IS NULL AND s.trialEndDate <= :sevenDayUntil) "
        + "OR (s.trialReminder1dSentAt IS NULL AND s.trialEndDate <= :oneDayUntil))")
    List<Subscription> findTrialReminderCandidates(@Param("now") OffsetDateTime now,
                                                   @Param("sevenDayUntil") OffsetDateTime sevenDayUntil,
                                                   @Param("oneDayUntil") OffsetDateTime oneDayUntil);

    /**
     * Candidates for the daily retention job (pre-Epic 10): rows that may be without access whose end of access
     * (access_ended_at, else trial_end_date for TRIAL, else next_billing_date) is by endBefore. Excludes the
     * statuses that always keep access (PAYMENT_FAILED, MONTHLY ACTIVE). The exact rules live in RetentionService.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status <> com.porquinho.entity.Subscription.Status.PAYMENT_FAILED "
        + "AND NOT (s.status = com.porquinho.entity.Subscription.Status.ACTIVE "
        + "AND s.planType = com.porquinho.entity.Subscription.PlanType.MONTHLY) "
        + "AND COALESCE(s.accessEndedAt, CASE WHEN s.planType = com.porquinho.entity.Subscription.PlanType.TRIAL "
        + "THEN s.trialEndDate ELSE s.nextBillingDate END) <= :endBefore")
    List<Subscription> findRetentionCandidates(@Param("endBefore") OffsetDateTime endBefore);

    /**
     * Hard-delete the user's subscription (Story 1.7 account purge, extended in Story 2.1).
     * Must run BEFORE deleting the user row (FK subscriptions.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM subscriptions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
