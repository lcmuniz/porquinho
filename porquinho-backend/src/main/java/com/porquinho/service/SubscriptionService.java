package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription rules (Story 2.1) and the user's own actions on the plan: monthly-plan Checkout (Story 2.2),
 * self-service cancellation at period end (Story 2.4; immediate while PAYMENT_FAILED, Story 2.7),
 * reactivation (Story 2.5) and the stop of billing on account deletion (retro Epic 2, F1).
 * What Stripe tells us through webhooks (activation, payments, failures, end of subscription) is applied
 * by SubscriptionWebhookService.
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
     * MONTHLY/CANCELLED keeps access while now is strictly before next_billing_date (end of the paid
     * period), even if Stripe's customer.subscription.deleted has not arrived yet (Story 2.4).
     * PAYMENT_FAILED keeps access while Stripe retries (Story 2.7 decision 2A); SUSPENDED has none.
     * No subscription means no access.
     */
    public boolean hasAccess(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
            .map(this::hasAccess)
            .orElse(false);
    }

    public boolean hasAccess(Subscription subscription) {
        if (subscription.getStatus() == Status.PAYMENT_FAILED) {
            return true;
        }
        if (subscription.getStatus() == Status.CANCELLED && subscription.getPlanType() == PlanType.MONTHLY) {
            OffsetDateTime accessUntil = subscription.getNextBillingDate();
            return accessUntil != null && OffsetDateTime.now(clock).isBefore(accessUntil);
        }
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
     * @throws ConflictException already MONTHLY/ACTIVE, cancelled within the paid period, or PAYMENT_FAILED
     *                           (the pending invoice must be paid instead, Story 2.7)
     * @throws PaymentProviderUnavailableException Stripe failed (trial is untouched)
     */
    public String createCheckout(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (subscription.getPlanType() == PlanType.MONTHLY && subscription.getStatus() == Status.ACTIVE) {
            throw new ConflictException("Você já possui uma assinatura ativa.");
        }
        // Cancelled but still in the paid period: a new Checkout would charge now on top of it (Story 2.4)
        if (subscription.getStatus() == Status.CANCELLED && hasAccess(subscription)) {
            throw new ConflictException("Sua assinatura cancelada ainda está ativa até o fim do período.");
        }
        // The Stripe subscription is alive and retrying: a new Checkout would create a second one (Story 2.7)
        if (subscription.getStatus() == Status.PAYMENT_FAILED) {
            throw new ConflictException("Seu último pagamento falhou. Pague a fatura pendente para manter sua assinatura.");
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
     * Undo a self-service cancel while the paid period is still running (Story 2.5, decision 1A).
     * Stripe is asked synchronously to drop the scheduled cancel (no charge now; next charge on the
     * date Stripe already had); only after Stripe accepted the row goes back to ACTIVE.
     * Expired (or cancelled with the period over) users reactivate through Checkout instead.
     *
     * @throws ResourceNotFoundException user has no subscription row
     * @throws ConflictException not MONTHLY/CANCELLED with access (active, trial, expired), or Stripe
     *                           says the subscription already ended
     * @throws PaymentProviderUnavailableException Stripe failed (nothing changes locally)
     */
    @Transactional
    public Subscription reactivate(UUID userId, String ipAddress) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (subscription.getPlanType() == PlanType.MONTHLY && subscription.getStatus() == Status.ACTIVE) {
            throw new ConflictException("Sua assinatura já está ativa.");
        }
        if (subscription.getStatus() == Status.PAYMENT_FAILED) {
            throw new ConflictException("Seu último pagamento falhou. Pague a fatura pendente para manter sua assinatura.");
        }
        if (subscription.getPlanType() != PlanType.MONTHLY
            || subscription.getStatus() != Status.CANCELLED
            || !hasAccess(subscription)) {
            throw new ConflictException(subscription.getPlanType() == PlanType.MONTHLY
                ? "Sua assinatura já terminou. Assine novamente para reativar."
                : "Não há assinatura cancelada para reativar.");
        }

        // Throws PaymentProviderUnavailableException -> 503 or ConflictException -> 409; row untouched.
        OffsetDateTime periodEnd = stripeBillingService.resumeSubscription(subscription.getStripeSubscriptionId());

        if (periodEnd != null) {
            subscription.setNextBillingDate(periodEnd);
        }
        OffsetDateTime nextBillingDate = subscription.getNextBillingDate();
        OffsetDateTime previousCancellationDate = subscription.getCancellationDate();
        subscription.setStatus(Status.ACTIVE);
        subscription.setCancellationDate(null);
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", subscription.getStripeSubscriptionId());
        metadata.put("next_billing_date", nextBillingDate == null ? null : nextBillingDate.toString());
        metadata.put("via", "resume");
        metadata.put("previous_cancellation_date",
            previousCancellationDate == null ? null : previousCancellationDate.toString());
        auditLogService.log("subscription_reactivated", userId, ipAddress, toJson(metadata));

        AfterCommit.emailUser(userRepository, userId, "about subscription reactivation",
            email -> emailService.sendSubscriptionReactivated(email, nextBillingDate));

        return saved;
    }

    /**
     * Self-service cancel of the monthly plan (Story 2.4). Stripe is asked synchronously to end the
     * subscription at period end (no further charge); only after Stripe accepted the row becomes
     * CANCELLED with cancellation_date. Access continues until next_billing_date.
     *
     * In PAYMENT_FAILED (Story 2.7 decision 1A) there is no paid period to keep: Stripe cancels right away
     * (stops retrying), the row becomes EXPIRED with cancellation_date and access ends now. The
     * customer.subscription.deleted that follows is a no-op.
     *
     * @throws ResourceNotFoundException user has no subscription row
     * @throws ConflictException not MONTHLY/ACTIVE or PAYMENT_FAILED (trial, already cancelled, expired, suspended)
     * @throws PaymentProviderUnavailableException Stripe failed (nothing changes locally)
     */
    @Transactional
    public Subscription cancel(UUID userId, String ipAddress) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (subscription.getPlanType() == PlanType.MONTHLY && subscription.getStatus() == Status.PAYMENT_FAILED) {
            return cancelImmediately(subscription, ipAddress);
        }
        if (subscription.getPlanType() != PlanType.MONTHLY || subscription.getStatus() != Status.ACTIVE) {
            throw new ConflictException(subscription.getStatus() == Status.CANCELLED
                ? "Sua assinatura já está cancelada."
                : "Não há assinatura mensal ativa para cancelar.");
        }

        // Throws PaymentProviderUnavailableException -> 503; the row is untouched (nothing set yet).
        // Stripe's period end is the source of truth: next_billing_date is not refreshed on renewals.
        OffsetDateTime periodEnd = stripeBillingService.cancelAtPeriodEnd(subscription.getStripeSubscriptionId());

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (periodEnd != null) {
            subscription.setNextBillingDate(periodEnd);
        }
        OffsetDateTime accessUntil = subscription.getNextBillingDate();
        subscription.setStatus(Status.CANCELLED);
        subscription.setCancellationDate(now);
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", subscription.getStripeSubscriptionId());
        metadata.put("cancellation_date", now.toString());
        metadata.put("access_until", accessUntil == null ? null : accessUntil.toString());
        auditLogService.log("subscription_cancelled", userId, ipAddress, toJson(metadata));

        AfterCommit.emailUser(userRepository, userId, "about subscription cancellation",
            email -> emailService.sendSubscriptionCancelled(email, accessUntil));

        return saved;
    }

    private Subscription cancelImmediately(Subscription subscription, String ipAddress) {
        // Throws PaymentProviderUnavailableException -> 503; the row is untouched (nothing set yet).
        String latestInvoiceId = stripeBillingService.cancelImmediately(subscription.getStripeSubscriptionId());

        OffsetDateTime now = OffsetDateTime.now(clock);
        int attempts = subscription.getFailedPaymentAttempts();
        subscription.setStatus(Status.EXPIRED);
        subscription.setCancellationDate(now);
        subscription.setPaymentUpdateUrl(null);
        Subscription saved = subscriptionRepository.save(subscription);

        UUID userId = subscription.getUserId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", subscription.getStripeSubscriptionId());
        metadata.put("cancellation_date", now.toString());
        metadata.put("access_until", now.toString());
        metadata.put("immediate", true);
        metadata.put("previous_status", Status.PAYMENT_FAILED.name());
        metadata.put("failed_payment_attempts", attempts);
        auditLogService.log("subscription_cancelled", userId, ipAddress, toJson(metadata));

        String stripeSubscriptionId = subscription.getStripeSubscriptionId();
        AfterCommit.run(() -> {
            // The failed invoice stays open after the cancel: void it so the e-mailed link stops charging
            voidOpenInvoice(stripeBillingService, stripeSubscriptionId, latestInvoiceId);
            AfterCommit.emailUserNow(userRepository, userId, "about subscription cancellation",
                emailService::sendSubscriptionCancelledImmediately);
        });

        return saved;
    }

    /**
     * Stop billing when the user asks to delete the account (retro Epic 2, F1). Joins the deletion
     * transaction: the row becomes EXPIRED with cancellation_date = now and no payment link, and
     * subscription_cancelled is audited (reason account_deletion, immediate). After commit, Stripe cancels
     * the subscription right away and, if the row was PAYMENT_FAILED, the open invoice is voided.
     * No refund of the current period (decision A) and no e-mail (the user just got the deletion one).
     *
     * Best-effort on Stripe: it never throws for a Stripe failure; the error is logged for a manual cancel
     * and the purge cancels again 30 days later. The customer.subscription.deleted that follows is a no-op.
     * No subscription, no Stripe id (trial only), or already EXPIRED/SUSPENDED: nothing changes.
     */
    @Transactional
    public void cancelForAccountDeletion(UUID userId) {
        Optional<Subscription> found = subscriptionRepository.findByUserIdForUpdate(userId);
        if (found.isEmpty()) {
            return;
        }
        Subscription subscription = found.get();
        String stripeSubscriptionId = subscription.getStripeSubscriptionId();
        Status previous = subscription.getStatus();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()
            || previous == Status.EXPIRED || previous == Status.SUSPENDED) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        subscription.setStatus(Status.EXPIRED);
        subscription.setCancellationDate(now);
        subscription.setPaymentUpdateUrl(null);
        subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", stripeSubscriptionId);
        metadata.put("cancellation_date", now.toString());
        metadata.put("access_until", now.toString());
        metadata.put("immediate", true);
        metadata.put("reason", "account_deletion");
        metadata.put("previous_status", previous.name());
        auditLogService.log("subscription_cancelled", userId, null, toJson(metadata));

        AfterCommit.run(() -> {
            String latestInvoiceId;
            try {
                latestInvoiceId = stripeBillingService.cancelImmediately(stripeSubscriptionId);
            } catch (RuntimeException e) {
                log.error("Could not cancel Stripe subscription {} of user {} on account deletion: {} - {}; "
                        + "cancel it by hand in the Stripe dashboard (the purge will retry)",
                    stripeSubscriptionId, userId, e.getClass().getSimpleName(), e.getMessage());
                return;
            }
            // A paid invoice cannot be voided: only the open failed one of a PAYMENT_FAILED row
            if (previous == Status.PAYMENT_FAILED) {
                voidOpenInvoice(stripeBillingService, stripeSubscriptionId, latestInvoiceId);
            }
        });
    }


    // Best-effort, never throws: a failure (or no invoice id) is logged for a manual void in the Stripe dashboard
    static void voidOpenInvoice(StripeBillingService stripeBillingService, String stripeSubscriptionId, String invoiceId) {
        if (invoiceId == null) {
            log.error("No invoice void for Stripe subscription {}: latest_invoice unknown; void it by hand",
                stripeSubscriptionId);
            return;
        }
        stripeBillingService.voidInvoice(invoiceId);
    }

    static String toJson(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize audit metadata", e);
        }
    }
}
