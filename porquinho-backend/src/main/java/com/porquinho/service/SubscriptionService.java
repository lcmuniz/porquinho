package com.porquinho.service;

import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription rules (Story 2.1).
 *
 * Trial expiration is computed on read (hasAccess compares now with trial_end_date).
 * The daily job that flips status to EXPIRED and sends reminders belongs to Story 2.3.
 */
@Service
public class SubscriptionService {

    public static final int TRIAL_DAYS = 30;

    private static final long SECONDS_PER_DAY = 86_400L;

    private final SubscriptionRepository subscriptionRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               AuditLogService auditLogService,
                               Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * Start the 30-day free trial for a newly registered user.
     * Idempotent: if the user already has a subscription, returns it unchanged (never restarts the trial).
     * Joins the caller's transaction, so a failure here rolls back the registration.
     */
    @Transactional
    public Subscription startTrial(UUID userId, String ipAddress) {
        Optional<Subscription> existing = subscriptionRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        OffsetDateTime start = OffsetDateTime.now(clock);
        OffsetDateTime end = start.plusDays(TRIAL_DAYS);
        Subscription saved = subscriptionRepository.save(
            new Subscription(userId, PlanType.TRIAL, Status.ACTIVE, start, end));

        auditLogService.log("trial_started", userId, ipAddress,
            String.format("{\"trial_end_date\":\"%s\"}", end));

        return saved;
    }

    public Optional<Subscription> findByUserId(UUID userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    /**
     * Access rule: status ACTIVE and, for TRIAL, now strictly before trial_end_date.
     * No subscription means no access.
     */
    public boolean hasAccess(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
            .map(this::hasAccess)
            .orElse(false);
    }

    public boolean hasAccess(Subscription subscription) {
        if (subscription.getStatus() != Status.ACTIVE) {
            return false;
        }
        if (subscription.getPlanType() != PlanType.TRIAL) {
            return true;
        }
        OffsetDateTime end = subscription.getTrialEndDate();
        return end != null && OffsetDateTime.now(clock).isBefore(end);
    }

    /**
     * Whole days left in the trial, rounded up (29d 1h -> 30). Never negative (expired -> 0).
     * Non-TRIAL plans always return 0 (trial dates are history, not a countdown).
     */
    public long daysRemaining(Subscription subscription) {
        if (subscription.getPlanType() != PlanType.TRIAL) {
            return 0;
        }
        OffsetDateTime end = subscription.getTrialEndDate();
        if (end == null) {
            return 0;
        }
        long seconds = Duration.between(OffsetDateTime.now(clock), end).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
    }
}
