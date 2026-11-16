package com.porquinho.service;

import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.BillingTransactionRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.porquinho.service.SubscriptionService.toJson;
import static com.porquinho.service.SubscriptionService.voidOpenInvoice;

/**
 * Subscription changes driven by Stripe webhooks (called only by StripeWebhookController): plan activation
 * after Checkout (Story 2.2, reactivation in 2.5), Stripe ending the subscription (Story 2.4; suspension after
 * failed retries in 2.7), monthly payments (Story 2.6, invoice.paid) and payment failures (Story 2.7:
 * invoice.payment_failed -> PAYMENT_FAILED; paid again -> ACTIVE; Stripe gave up -> SUSPENDED).
 * Retries are Stripe's (Dashboard schedule): nothing here charges or retries on its own.
 *
 * The access rule and the user's own actions live in SubscriptionService.
 */
@Service
public class SubscriptionWebhookService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionWebhookService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final StripeBillingService stripeBillingService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final BillingTransactionRepository billingTransactionRepository;
    private final String subscriptionPageUrl;

    public SubscriptionWebhookService(SubscriptionRepository subscriptionRepository,
                                      AuditLogService auditLogService,
                                      Clock clock,
                                      StripeBillingService stripeBillingService,
                                      UserRepository userRepository,
                                      EmailService emailService,
                                      BillingTransactionRepository billingTransactionRepository,
                                      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.stripeBillingService = stripeBillingService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.billingTransactionRepository = billingTransactionRepository;
        String base = frontendUrl == null ? "" : frontendUrl;
        this.subscriptionPageUrl = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/assinatura";
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
     * A declined Stripe invoice, as read from the invoice.payment_failed webhook (Story 2.7).
     *
     * @param invoiceId Stripe invoice id (in_...)
     * @param stripeSubscriptionId Stripe subscription the invoice belongs to
     * @param attemptCount Stripe's attempt_count (1 = first charge, 2+ = retries); the idempotency key
     * @param hostedInvoiceUrl hosted_invoice_url: page where the user pays the invoice (saved or new card); may be null
     * @param willRetry next_payment_attempt is set: Stripe will retry; false on the last attempt (suspension follows)
     */
    public record PaymentFailure(String invoiceId, String stripeSubscriptionId, long attemptCount,
                                 String hostedInvoiceUrl, boolean willRetry) {
    }

    /** What {@link #recordPaymentFailure(PaymentFailure)} did with the failure. */
    public enum FailureOutcome {
        /** Row is PAYMENT_FAILED with the new attempt count, audit and e-mail. */
        RECORDED,
        /** Replay (attempt_count already recorded), invoice already paid, or a status that cannot fail. */
        IGNORED,
        /** No local row with this Stripe subscription id: nothing changed. */
        UNKNOWN_SUBSCRIPTION
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

        // Account deletion requested (user soft-deleted, filtered out by @Where): a Checkout completed late,
        // or an old one re-delivered, must not bring billing back. Cancel the incoming Stripe subscription.
        if (userRepository.findById(userId).isEmpty()) {
            log.warn("Checkout completed for deleted user {}; cancelling Stripe subscription {}",
                userId, stripeSubscriptionId);
            stripeBillingService.cancelAndRefund(stripeSubscriptionId, RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
            return subscription;
        }

        // Second completed Checkout (two tabs / re-click) for an already active plan: keep the
        // current Stripe subscription, cancel the incoming one and refund its charge (retro Epic 2 F2).
        // PAYMENT_FAILED too: that Stripe subscription is still alive and retrying (Story 2.7).
        if (subscription.getPlanType() == PlanType.MONTHLY
            && (subscription.getStatus() == Status.ACTIVE || subscription.getStatus() == Status.PAYMENT_FAILED)
            && subscription.getStripeSubscriptionId() != null) {
            log.error("User {} already has active Stripe subscription {}; cancelling duplicate {}",
                userId, subscription.getStripeSubscriptionId(), stripeSubscriptionId);
            // Its first charge (outside the trial) is refunded in full; a R$ 0 trial invoice has nothing to refund
            stripeBillingService.cancelAndRefund(stripeSubscriptionId, RefundCreateParams.Reason.DUPLICATE);
            return subscription;
        }

        // Former subscriber (EXPIRED, SUSPENDED, or CANCELLED with the paid period over) back via Checkout (Story 2.5)
        boolean reactivation = subscription.getPlanType() == PlanType.MONTHLY;

        subscription.setPlanType(PlanType.MONTHLY);
        subscription.setStatus(Status.ACTIVE);
        subscription.setStripeCustomerId(stripeCustomerId);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setPaymentMethodId(paymentMethodId);
        subscription.setNextBillingDate(nextBillingDate);
        subscription.setCancellationDate(null);
        subscription.setFailedPaymentAttempts(0);
        subscription.setPaymentUpdateUrl(null);
        // Back with access: the retention countdown (purge 30 days after access ended) starts over
        subscription.setAccessEndedAt(null);
        subscription.setRetentionWarning7dSentAt(null);
        subscription.setRetentionWarning1dSentAt(null);
        Subscription saved = subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", stripeSubscriptionId);
        metadata.put("next_billing_date", nextBillingDate == null ? null : nextBillingDate.toString());
        if (reactivation) {
            metadata.put("via", "checkout");
        }
        auditLogService.log(reactivation ? "subscription_reactivated" : "subscription_activated",
            userId, null, toJson(metadata));

        AfterCommit.emailUser(userRepository, userId, "about subscription activation", email -> {
            if (reactivation) {
                emailService.sendSubscriptionReactivated(email, nextBillingDate);
            } else {
                emailService.sendSubscriptionActivated(email, nextBillingDate);
            }
        });

        return saved;
    }

    /**
     * Stripe ended the subscription (customer.subscription.deleted webhook, Story 2.4): mark the row
     * EXPIRED. Unknown id -> nothing changes (warn). Already EXPIRED/SUSPENDED -> no-op (webhook replay).
     * From PAYMENT_FAILED it means every retry failed and Stripe cancelled it (Story 2.7): SUSPENDED,
     * audit subscription_suspended, the "Assinatura suspensa" e-mail and, after commit, a best-effort void
     * of the subscription's open invoice (decision 2A: the e-mailed payment link stops charging).
     *
     * @param latestInvoiceId latest_invoice of the deleted subscription (the one that failed); may be null
     * @return true if a row was expired or suspended by this call
     */
    @Transactional
    public boolean expireByStripeSubscriptionId(String stripeSubscriptionId, String latestInvoiceId) {
        Optional<Subscription> found = stripeSubscriptionId == null
            ? Optional.empty()
            : subscriptionRepository.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId);
        if (found.isEmpty()) {
            log.warn("customer.subscription.deleted for unknown Stripe subscription {}", stripeSubscriptionId);
            return false;
        }
        Subscription subscription = found.get();
        if (subscription.getStatus() == Status.EXPIRED || subscription.getStatus() == Status.SUSPENDED) {
            return false;
        }
        if (subscription.getStatus() == Status.PAYMENT_FAILED) {
            suspend(subscription, latestInvoiceId);
            return true;
        }

        Status previous = subscription.getStatus();
        subscription.setStatus(Status.EXPIRED);
        subscription.setAccessEndedAt(OffsetDateTime.now(clock));
        subscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", stripeSubscriptionId);
        metadata.put("previous_status", previous.name());
        auditLogService.log("subscription_expired", subscription.getUserId(), null, toJson(metadata));
        return true;
    }

    private void suspend(Subscription subscription, String latestInvoiceId) {
        int attempts = subscription.getFailedPaymentAttempts();
        subscription.setStatus(Status.SUSPENDED);
        subscription.setAccessEndedAt(OffsetDateTime.now(clock));
        // The failed invoice is closed with the subscription: its page no longer settles anything
        subscription.setPaymentUpdateUrl(null);
        subscriptionRepository.save(subscription);

        UUID userId = subscription.getUserId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stripe_subscription_id", subscription.getStripeSubscriptionId());
        metadata.put("failed_payment_attempts", attempts);
        metadata.put("latest_invoice_id", latestInvoiceId);
        auditLogService.log("subscription_suspended", userId, null, toJson(metadata));

        String reactivateUrl = subscriptionPageUrl;
        AfterCommit.run(() -> {
            voidOpenInvoice(stripeBillingService, subscription.getStripeSubscriptionId(), latestInvoiceId);
            AfterCommit.emailUserNow(userRepository, userId, "about subscription suspension",
                email -> emailService.sendSubscriptionSuspended(email, reactivateUrl));
        });
    }

    /**
     * Record a declined renewal (invoice.payment_failed webhook, Story 2.7). Stripe retries on its own
     * schedule; this marks the row PAYMENT_FAILED (access kept, decision 2A), stores attempt_count and the
     * invoice's payment page, audits payment_failed and e-mails "Pagamento falhou" after commit.
     *
     * Idempotent by attempt_count: an attempt at or below the recorded one changes nothing. A failure of
     * an invoice already paid (out-of-order delivery) is ignored, as is any status other than
     * MONTHLY ACTIVE/PAYMENT_FAILED. Nothing is written to billing_transactions (the invoice id is UNIQUE
     * there and must stay free for the payment that later succeeds).
     */
    @Transactional
    public FailureOutcome recordPaymentFailure(PaymentFailure failure) {
        Optional<Subscription> found = failure.stripeSubscriptionId() == null
            ? Optional.empty()
            : subscriptionRepository.findByStripeSubscriptionIdForUpdate(failure.stripeSubscriptionId());
        if (found.isEmpty()) {
            return FailureOutcome.UNKNOWN_SUBSCRIPTION;
        }
        Subscription subscription = found.get();

        Status previous = subscription.getStatus();
        if (subscription.getPlanType() != PlanType.MONTHLY
            || (previous != Status.ACTIVE && previous != Status.PAYMENT_FAILED)) {
            log.warn("Ignoring payment failure of invoice {}: subscription {} is {}/{}",
                failure.invoiceId(), failure.stripeSubscriptionId(), subscription.getPlanType(), previous);
            return FailureOutcome.IGNORED;
        }
        if (failure.attemptCount() <= subscription.getFailedPaymentAttempts()) {
            return FailureOutcome.IGNORED;
        }
        if (failure.invoiceId() != null && billingTransactionRepository.existsByStripeTransactionId(failure.invoiceId())) {
            return FailureOutcome.IGNORED;
        }

        int attempts = (int) Math.min(failure.attemptCount(), Integer.MAX_VALUE);
        subscription.setStatus(Status.PAYMENT_FAILED);
        subscription.setFailedPaymentAttempts(attempts);
        if (failure.hostedInvoiceUrl() != null) {
            subscription.setPaymentUpdateUrl(failure.hostedInvoiceUrl());
        }
        subscriptionRepository.save(subscription);
        String payUrl = subscription.getPaymentUpdateUrl() != null ? subscription.getPaymentUpdateUrl() : subscriptionPageUrl;

        UUID userId = subscription.getUserId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attempt_count", attempts);
        metadata.put("invoice_id", failure.invoiceId());
        metadata.put("previous_status", previous.name());
        auditLogService.log("payment_failed", userId, null, toJson(metadata));

        boolean willRetry = failure.willRetry();
        AfterCommit.emailUser(userRepository, userId, "about the failed payment",
            email -> emailService.sendPaymentFailed(email, payUrl, willRetry));

        return FailureOutcome.RECORDED;
    }

    /**
     * Record a monthly payment Stripe already charged (invoice.paid webhook, Story 2.6). Stripe is the
     * one charging; this only writes billing_transactions, moves next_billing_date to the end of the
     * new period (only forward), audits payment_success and e-mails the receipt after commit.
     *
     * The row is locked directly by Stripe subscription id (no prior read, so the lock re-reads it),
     * which serializes concurrent deliveries of the same invoice; the invoice id makes replays no-ops.
     * The new period end comes from Stripe; if Stripe is down, the invoice line's period end is used.
     * A row in PAYMENT_FAILED is recovered (Story 2.7): back to ACTIVE, counter and payment link cleared,
     * audit payment_recovered. A row already SUSPENDED (the old invoice was paid because its void failed)
     * is recorded as usual but stays SUSPENDED: logged at error level for a manual refund.
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
        UUID userId = subscription.getUserId();

        if (subscription.getStatus() == Status.PAYMENT_FAILED) {
            int attempts = subscription.getFailedPaymentAttempts();
            subscription.setStatus(Status.ACTIVE);
            subscription.setFailedPaymentAttempts(0);
            subscription.setPaymentUpdateUrl(null);
            subscriptionRepository.save(subscription);

            Map<String, Object> recovered = new LinkedHashMap<>();
            recovered.put("invoice_id", payment.invoiceId());
            recovered.put("failed_payment_attempts", attempts);
            auditLogService.log("payment_recovered", userId, null, toJson(recovered));
        } else if (subscription.getStatus() == Status.SUSPENDED || subscription.getStatus() == Status.EXPIRED) {
            log.error("Invoice {} paid for {} subscription {} (user {}): payment recorded without access; "
                    + "refund it by hand in the Stripe dashboard",
                payment.invoiceId(), subscription.getStatus(), payment.stripeSubscriptionId(), userId);
        }

        BigDecimal amount = BigDecimal.valueOf(payment.amountPaid(), 2);
        // currency is NOT NULL; the single plan is priced in BRL, so a missing field defaults to it
        String currency = payment.currency() == null ? "brl" : payment.currency().toLowerCase(Locale.ROOT);
        OffsetDateTime paidAt = payment.paidAt() != null ? payment.paidAt() : OffsetDateTime.now(clock);

        billingTransactionRepository.save(new BillingTransaction(subscription.getId(), userId, amount, currency,
            BillingTransaction.Status.PAID, subscription.getPaymentMethodId(), payment.invoiceId(), paidAt));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("amount", amount);
        metadata.put("currency", currency);
        metadata.put("invoice_id", payment.invoiceId());
        metadata.put("next_billing_date", nextBillingDate == null ? null : nextBillingDate.toString());
        auditLogService.log("payment_success", userId, null, toJson(metadata));

        AfterCommit.emailUser(userRepository, userId, "the payment receipt",
            email -> emailService.sendPaymentReceipt(email, amount, paidAt, nextBillingDate));

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
}
