package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription rules (Story 2.1) and monthly-plan activation via Stripe (Story 2.2).
 *
 * Trial expiration is computed on read (hasAccess compares now with trial_end_date).
 * Trial-end reminder e-mails are sent by TrialReminderService (Story 2.3).
 */
@Service
public class SubscriptionService {

    public static final int TRIAL_DAYS = 30;

    /**
     * Stripe Checkout rejects subscription_data.trial_end less than 48h ahead. If the local trial
     * ends sooner, the first charge is pushed to this minimum (the user gets a few extra hours free).
     */
    static final Duration MIN_STRIPE_TRIAL = Duration.ofHours(49);

    private static final long SECONDS_PER_DAY = 86_400L;

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SubscriptionRepository subscriptionRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final StripeBillingService stripeBillingService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               AuditLogService auditLogService,
                               Clock clock,
                               StripeBillingService stripeBillingService,
                               UserRepository userRepository,
                               EmailService emailService) {
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.stripeBillingService = stripeBillingService;
        this.userRepository = userRepository;
        this.emailService = emailService;
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

    /**
     * Start a Stripe Checkout for the monthly plan and return the hosted page URL (Story 2.2).
     * Nothing changes locally: only the verified webhook activates the plan.
     *
     * During an active trial the first charge is scheduled for trial_end_date (decision 2026-10-31),
     * so the user keeps the free days. With the trial over, Stripe charges immediately.
     *
     * @throws ResourceNotFoundException user has no subscription row
     * @throws ConflictException already MONTHLY/ACTIVE
     * @throws PaymentProviderUnavailableException Stripe failed (trial is untouched)
     */
    public String createCheckout(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (subscription.getPlanType() == PlanType.MONTHLY && subscription.getStatus() == Status.ACTIVE) {
            throw new ConflictException("Você já possui uma assinatura ativa.");
        }

        String email = userRepository.findById(userId).map(User::getEmail).orElse(null);
        return stripeBillingService.createCheckoutSession(userId, email, firstChargeAt(subscription));
    }

    /**
     * When Stripe should charge for the first time: end of the running trial, or null (charge now).
     */
    Instant firstChargeAt(Subscription subscription) {
        if (subscription.getPlanType() != PlanType.TRIAL || !hasAccess(subscription)) {
            return null;
        }
        Instant trialEnd = subscription.getTrialEndDate().toInstant();
        Instant minimum = clock.instant().plus(MIN_STRIPE_TRIAL);
        return trialEnd.isBefore(minimum) ? minimum : trialEnd;
    }

    /**
     * Switch the user to MONTHLY/ACTIVE after Stripe confirmed the Checkout (webhook only).
     * Idempotent: replaying the same Stripe subscription changes nothing and logs no new audit.
     *
     * @throws ResourceNotFoundException user has no subscription row
     */
    @Transactional
    public Subscription activateMonthly(UUID userId, String stripeCustomerId, String stripeSubscriptionId,
                                        String paymentMethodId, OffsetDateTime nextBillingDate) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (subscription.getPlanType() == PlanType.MONTHLY
            && subscription.getStatus() == Status.ACTIVE
            && Objects.equals(subscription.getStripeSubscriptionId(), stripeSubscriptionId)) {
            return subscription;
        }

        // Second completed Checkout (two tabs / re-click) for an already active plan: keep the
        // current Stripe subscription and cancel the incoming one so the user is not billed twice.
        if (subscription.getPlanType() == PlanType.MONTHLY
            && subscription.getStatus() == Status.ACTIVE
            && subscription.getStripeSubscriptionId() != null) {
            log.error("User {} already has active Stripe subscription {}; cancelling duplicate {}",
                userId, subscription.getStripeSubscriptionId(), stripeSubscriptionId);
            stripeBillingService.cancelSubscription(stripeSubscriptionId);
            return subscription;
        }

        subscription.setPlanType(PlanType.MONTHLY);
        subscription.setStatus(Status.ACTIVE);
        subscription.setStripeCustomerId(stripeCustomerId);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setPaymentMethodId(paymentMethodId);
        subscription.setNextBillingDate(nextBillingDate);
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", stripeSubscriptionId);
        metadata.put("next_billing_date", nextBillingDate == null ? null : nextBillingDate.toString());
        auditLogService.log("subscription_activated", userId, null, toJson(metadata));

        afterCommit(() -> userRepository.findById(userId)
            .map(User::getEmail)
            .ifPresentOrElse(
                email -> emailService.sendSubscriptionActivated(email, nextBillingDate),
                () -> log.warn("No user {} to email about subscription activation", userId)));

        return saved;
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize audit metadata", e);
        }
    }

    // E-mail only after the row is committed, and outside the row lock. Also used by TrialReminderService.
    static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
