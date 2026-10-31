package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.BillingTransactionRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription rules (Story 2.1), monthly-plan activation via Stripe (Story 2.2) and
 * self-service cancellation at period end (Story 2.4) and reactivation (Story 2.5), and recording of
 * the monthly payments Stripe charges (Story 2.6, invoice.paid webhook).
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
    private final BillingTransactionRepository billingTransactionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               AuditLogService auditLogService,
                               Clock clock,
                               StripeBillingService stripeBillingService,
                               UserRepository userRepository,
                               EmailService emailService,
                               BillingTransactionRepository billingTransactionRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.stripeBillingService = stripeBillingService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.billingTransactionRepository = billingTransactionRepository;
    }

    /**
     * A paid Stripe invoice, as read from the invoice.paid webhook (Story 2.6).
     *
     * @param invoiceId Stripe invoice id (in_...), the idempotency key
     * @param stripeSubscriptionId Stripe subscription the invoice belongs to
     * @param amountPaid amount_paid in cents (1990 = R$ 19,90)
     * @param currency ISO 4217 code as Stripe sends it (stored lower-case)
     * @param paidAt status_transitions.paid_at; null -> now
     * @param linePeriodEnd lines.data[0].period.end, fallback for next_billing_date; may be null
     */
    public record InvoicePayment(String invoiceId, String stripeSubscriptionId, long amountPaid, String currency,
                                 OffsetDateTime paidAt, OffsetDateTime linePeriodEnd) {
    }

    /** What {@link #recordPayment(InvoicePayment)} did with the invoice. */
    public enum PaymentOutcome {
        /** New billing_transactions row, next_billing_date updated, audit and receipt e-mail. */
        RECORDED,
        /** Invoice already recorded (webhook replay): nothing changed. */
        DUPLICATE,
        /** No local row with this Stripe subscription id: nothing changed. */
        UNKNOWN_SUBSCRIPTION
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
     * No subscription means no access.
     */
    public boolean hasAccess(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
            .map(this::hasAccess)
            .orElse(false);
    }

    public boolean hasAccess(Subscription subscription) {
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
     * @throws ConflictException already MONTHLY/ACTIVE
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
     * Idempotent: replaying the same Stripe subscription changes nothing and logs no new audit,
     * whatever the local status. An old checkout.session.completed re-delivered after the user
     * cancelled (or it expired) never reactivates the row (Story 2.4).
     *
     * @throws ResourceNotFoundException user has no subscription row
     */
    @Transactional
    public Subscription activateMonthly(UUID userId, String stripeCustomerId, String stripeSubscriptionId,
                                        String paymentMethodId, OffsetDateTime nextBillingDate) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (stripeSubscriptionId != null
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

        // Former subscriber (EXPIRED, or CANCELLED with the paid period over) coming back via Checkout (Story 2.5)
        boolean reactivation = subscription.getPlanType() == PlanType.MONTHLY;

        subscription.setPlanType(PlanType.MONTHLY);
        subscription.setStatus(Status.ACTIVE);
        subscription.setStripeCustomerId(stripeCustomerId);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setPaymentMethodId(paymentMethodId);
        subscription.setNextBillingDate(nextBillingDate);
        subscription.setCancellationDate(null);
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", stripeSubscriptionId);
        metadata.put("next_billing_date", nextBillingDate == null ? null : nextBillingDate.toString());
        if (reactivation) {
            metadata.put("via", "checkout");
        }
        auditLogService.log(reactivation ? "subscription_reactivated" : "subscription_activated",
            userId, null, toJson(metadata));

        afterCommit(() -> userRepository.findById(userId)
            .map(User::getEmail)
            .ifPresentOrElse(
                email -> {
                    if (reactivation) {
                        emailService.sendSubscriptionReactivated(email, nextBillingDate);
                    } else {
                        emailService.sendSubscriptionActivated(email, nextBillingDate);
                    }
                },
                () -> log.warn("No user {} to email about subscription activation", userId)));

        return saved;
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

        afterCommit(() -> userRepository.findById(userId)
            .map(User::getEmail)
            .ifPresentOrElse(
                email -> emailService.sendSubscriptionReactivated(email, nextBillingDate),
                () -> log.warn("No user {} to email about subscription reactivation", userId)));

        return saved;
    }

    /**
     * Self-service cancel of the monthly plan (Story 2.4). Stripe is asked synchronously to end the
     * subscription at period end (no further charge); only after Stripe accepted the row becomes
     * CANCELLED with cancellation_date. Access continues until next_billing_date.
     *
     * @throws ResourceNotFoundException user has no subscription row
     * @throws ConflictException not MONTHLY/ACTIVE (trial, already cancelled, expired)
     * @throws PaymentProviderUnavailableException Stripe failed (nothing changes locally)
     */
    @Transactional
    public Subscription cancel(UUID userId, String ipAddress) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

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

        afterCommit(() -> userRepository.findById(userId)
            .map(User::getEmail)
            .ifPresentOrElse(
                email -> emailService.sendSubscriptionCancelled(email, accessUntil),
                () -> log.warn("No user {} to email about subscription cancellation", userId)));

        return saved;
    }

    /**
     * Stripe ended the subscription (customer.subscription.deleted webhook, Story 2.4): mark the row
     * EXPIRED. Unknown id -> nothing changes (warn). Already EXPIRED -> no-op (webhook replay).
     *
     * @return true if a row was expired by this call
     */
    @Transactional
    public boolean expireByStripeSubscriptionId(String stripeSubscriptionId) {
        Optional<Subscription> found = stripeSubscriptionId == null
            ? Optional.empty()
            : subscriptionRepository.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId);
        if (found.isEmpty()) {
            log.warn("customer.subscription.deleted for unknown Stripe subscription {}", stripeSubscriptionId);
            return false;
        }
        Subscription subscription = found.get();
        if (subscription.getStatus() == Status.EXPIRED) {
            return false;
        }

        Status previous = subscription.getStatus();
        subscription.setStatus(Status.EXPIRED);
        subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", stripeSubscriptionId);
        metadata.put("previous_status", previous.name());
        auditLogService.log("subscription_expired", subscription.getUserId(), null, toJson(metadata));
        return true;
    }

    /**
     * Record a monthly payment Stripe already charged (invoice.paid webhook, Story 2.6). Stripe is the
     * one charging; this only writes billing_transactions, moves next_billing_date to the end of the
     * new period (only forward), audits payment_success and e-mails the receipt after commit.
     *
     * The row is locked directly by Stripe subscription id (no prior read, so the lock re-reads it),
     * which serializes concurrent deliveries of the same invoice; the invoice id makes replays no-ops.
     * The new period end comes from Stripe; if Stripe is down, the invoice line's period end is used.
     */
    @Transactional
    public PaymentOutcome recordPayment(InvoicePayment payment) {
        Optional<Subscription> found = payment.stripeSubscriptionId() == null
            ? Optional.empty()
            : subscriptionRepository.findByStripeSubscriptionIdForUpdate(payment.stripeSubscriptionId());
        if (found.isEmpty()) {
            return PaymentOutcome.UNKNOWN_SUBSCRIPTION;
        }
        Subscription subscription = found.get();

        if (billingTransactionRepository.existsByStripeTransactionId(payment.invoiceId())) {
            return PaymentOutcome.DUPLICATE;
        }

        OffsetDateTime periodEnd = currentPeriodEnd(payment);
        OffsetDateTime current = subscription.getNextBillingDate();
        if (periodEnd == null) {
            log.warn("next_billing_date not advanced for invoice {}: no period end from Stripe nor invoice line",
                payment.invoiceId());
        } else if (current == null || periodEnd.isAfter(current)) {
            subscription.setNextBillingDate(periodEnd);
            subscriptionRepository.save(subscription);
        }
        OffsetDateTime nextBillingDate = subscription.getNextBillingDate();

        BigDecimal amount = BigDecimal.valueOf(payment.amountPaid(), 2);
        // currency is NOT NULL; the single plan is priced in BRL, so a missing field defaults to it
        String currency = payment.currency() == null ? "brl" : payment.currency().toLowerCase(Locale.ROOT);
        OffsetDateTime paidAt = payment.paidAt() != null ? payment.paidAt() : OffsetDateTime.now(clock);
        UUID userId = subscription.getUserId();

        billingTransactionRepository.save(new BillingTransaction(subscription.getId(), userId, amount, currency,
            BillingTransaction.Status.PAID, subscription.getPaymentMethodId(), payment.invoiceId(), paidAt));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("amount", amount);
        metadata.put("currency", currency);
        metadata.put("invoice_id", payment.invoiceId());
        metadata.put("next_billing_date", nextBillingDate == null ? null : nextBillingDate.toString());
        auditLogService.log("payment_success", userId, null, toJson(metadata));

        afterCommit(() -> userRepository.findById(userId)
            .map(User::getEmail)
            .ifPresentOrElse(
                email -> emailService.sendPaymentReceipt(email, amount, paidAt, nextBillingDate),
                () -> log.warn("No user {} to email the payment receipt", userId)));

        return PaymentOutcome.RECORDED;
    }

    // Stripe's current_period_end is the source of truth; the invoice line is the fallback when Stripe is down
    private OffsetDateTime currentPeriodEnd(InvoicePayment payment) {
        try {
            OffsetDateTime periodEnd = stripeBillingService
                .retrieveSubscription(payment.stripeSubscriptionId())
                .currentPeriodEnd();
            if (periodEnd != null) {
                return periodEnd;
            }
        } catch (PaymentProviderUnavailableException e) {
            log.warn("Stripe unavailable fetching period of subscription {} for invoice {}; using invoice line period end",
                payment.stripeSubscriptionId(), payment.invoiceId());
        }
        return payment.linePeriodEnd();
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
