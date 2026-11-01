package com.porquinho.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.StripeBillingService;
import com.porquinho.service.StripeBillingService.SubscriptionDetails;
import com.porquinho.service.StripeBillingService.WebhookEvent;
import com.porquinho.service.SubscriptionService;
import com.porquinho.service.SubscriptionService.FailureOutcome;
import com.porquinho.service.SubscriptionService.InvoicePayment;
import com.porquinho.service.SubscriptionService.PaymentFailure;
import com.porquinho.service.SubscriptionService.PaymentOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Stripe webhook (Story 2.2). Public in SecurityConfig: trust comes from the Stripe-Signature
 * header, verified against STRIPE_WEBHOOK_SECRET. Handles checkout.session.completed (Story 2.2) and
 * customer.subscription.deleted (Story 2.4: subscription ended -> EXPIRED) and invoice.paid (Story 2.6:
 * record the monthly payment Stripe charged) and invoice.payment_failed (Story 2.7: renewal declined,
 * Stripe retries; with every retry failed, customer.subscription.deleted suspends the row); other events
 * get 200 so Stripe stops retrying them.
 *
 * Errors: bad signature -> 400 (nothing changes); Stripe API down while fetching the
 * subscription on checkout -> 503 (Stripe retries the delivery); first invoice of a subscription
 * (billing_reason=subscription_create) arriving before checkout.session.completed -> 503, so the
 * retry records it once the subscription is known.
 */
@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    static final String CHECKOUT_COMPLETED = "checkout.session.completed";
    static final String SUBSCRIPTION_DELETED = "customer.subscription.deleted";
    static final String INVOICE_PAID = "invoice.paid";
    static final String INVOICE_PAYMENT_FAILED = "invoice.payment_failed";

    private static final String BILLING_REASON_SUBSCRIPTION_CREATE = "subscription_create";

    // "no_payment_required": subscribed during the trial, first charge on trial_end
    private static final Set<String> SETTLED_PAYMENT_STATUSES = Set.of("paid", "no_payment_required");

    private final StripeBillingService stripeBillingService;
    private final SubscriptionService subscriptionService;

    public StripeWebhookController(StripeBillingService stripeBillingService,
                                   SubscriptionService subscriptionService) {
        this.stripeBillingService = stripeBillingService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping(path = "/api/v1/webhooks/stripe")
    public ResponseEntity<Void> handle(@RequestBody(required = false) String payload,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        WebhookEvent event;
        try {
            event = stripeBillingService.verifyWebhook(payload, signature);
        } catch (InvalidWebhookSignatureException e) {
            log.warn("Rejected Stripe webhook: {}", e.getMessage());
            throw e;
        }

        if (CHECKOUT_COMPLETED.equals(event.type())) {
            handleCheckoutCompleted(event);
        } else if (SUBSCRIPTION_DELETED.equals(event.type())) {
            handleSubscriptionDeleted(event);
        } else if (INVOICE_PAID.equals(event.type())) {
            return handleInvoicePaid(event);
        } else if (INVOICE_PAYMENT_FAILED.equals(event.type())) {
            handleInvoicePaymentFailed(event);
        } else {
            log.debug("Ignoring Stripe event {} ({})", event.id(), event.type());
        }
        return ResponseEntity.ok().build();
    }

    private void handleSubscriptionDeleted(WebhookEvent event) {
        String stripeSubscriptionId = text(event.object(), "id");
        if (stripeSubscriptionId == null) {
            log.warn("Ignoring {} {}: no subscription id", SUBSCRIPTION_DELETED, event.id());
            return;
        }
        // latest_invoice: the open invoice voided on suspension (Story 2.7); a string id, or an object if expanded
        JsonNode latestInvoice = path(event.object(), "latest_invoice");
        String latestInvoiceId = latestInvoice != null && latestInvoice.isObject()
            ? text(latestInvoice, "id")
            : text(event.object(), "latest_invoice");
        // Unknown id / replay: service logs and changes nothing; always 200 so Stripe stops retrying
        if (subscriptionService.expireByStripeSubscriptionId(stripeSubscriptionId, latestInvoiceId)) {
            log.info("Stripe subscription {} ended; local subscription expired or suspended (event {})",
                stripeSubscriptionId, event.id());
        }
    }

    private ResponseEntity<Void> handleInvoicePaid(WebhookEvent event) {
        JsonNode invoice = event.object();
        String invoiceId = text(invoice, "id");
        String stripeSubscriptionId = invoiceSubscriptionId(invoice);
        long amountPaid = invoice == null ? 0 : invoice.path("amount_paid").asLong(0);

        if (invoiceId == null) {
            log.warn("Ignoring {} {}: no invoice id", INVOICE_PAID, event.id());
            return ResponseEntity.ok().build();
        }
        if (stripeSubscriptionId == null) {
            log.debug("Ignoring {} {}: invoice {} has no subscription", INVOICE_PAID, event.id(), invoiceId);
            return ResponseEntity.ok().build();
        }
        if (amountPaid <= 0) {
            // Subscribed during the trial: Stripe issues a R$ 0 invoice on creation
            log.debug("Ignoring {} {}: invoice {} amount_paid={}", INVOICE_PAID, event.id(), invoiceId, amountPaid);
            return ResponseEntity.ok().build();
        }

        InvoicePayment payment = new InvoicePayment(invoiceId, stripeSubscriptionId, amountPaid,
            text(invoice, "currency"),
            epochSeconds(path(invoice, "status_transitions"), "paid_at"),
            epochSeconds(path(path(invoice, "lines", "data"), 0, "period"), "end"));
        PaymentOutcome outcome = subscriptionService.recordPayment(payment);

        if (outcome == PaymentOutcome.UNKNOWN_SUBSCRIPTION) {
            String billingReason = text(invoice, "billing_reason");
            if (BILLING_REASON_SUBSCRIPTION_CREATE.equals(billingReason)) {
                // First invoice raced ahead of checkout.session.completed: 503 so Stripe re-delivers it
                log.info("{} {}: first invoice {} for Stripe subscription {} arrived before checkout; asking Stripe to retry",
                    INVOICE_PAID, event.id(), invoiceId, stripeSubscriptionId);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            log.warn("Ignoring {} {}: unknown Stripe subscription {} (invoice {}, billing_reason={})",
                INVOICE_PAID, event.id(), stripeSubscriptionId, invoiceId, billingReason);
        } else if (outcome == PaymentOutcome.DUPLICATE) {
            log.debug("Ignoring {} {}: invoice {} already recorded", INVOICE_PAID, event.id(), invoiceId);
        } else if (outcome == PaymentOutcome.RECORDED) {
            log.info("Payment recorded for Stripe subscription {} (invoice {}, event {})",
                stripeSubscriptionId, invoiceId, event.id());
        }
        return ResponseEntity.ok().build();
    }

    // Always 200: Stripe retries the charge itself; re-delivering the event would not change anything here
    private void handleInvoicePaymentFailed(WebhookEvent event) {
        JsonNode invoice = event.object();
        String invoiceId = text(invoice, "id");
        String stripeSubscriptionId = invoiceSubscriptionId(invoice);

        if (invoiceId == null) {
            log.warn("Ignoring {} {}: no invoice id", INVOICE_PAYMENT_FAILED, event.id());
            return;
        }
        if (stripeSubscriptionId == null) {
            log.debug("Ignoring {} {}: invoice {} has no subscription", INVOICE_PAYMENT_FAILED, event.id(), invoiceId);
            return;
        }

        PaymentFailure failure = new PaymentFailure(invoiceId, stripeSubscriptionId,
            invoice.path("attempt_count").asLong(0),
            text(invoice, "hosted_invoice_url"),
            // null on the last attempt: Stripe will not retry and cancels the subscription (suspension)
            text(invoice, "next_payment_attempt") != null);
        FailureOutcome outcome = subscriptionService.recordPaymentFailure(failure);

        if (outcome == FailureOutcome.UNKNOWN_SUBSCRIPTION) {
            // Typically the first invoice declined inside Checkout: the subscription was never activated
            // here and Checkout itself shows the error to the user
            log.warn("Ignoring {} {}: unknown Stripe subscription {} (invoice {}, billing_reason={})",
                INVOICE_PAYMENT_FAILED, event.id(), stripeSubscriptionId, invoiceId, text(invoice, "billing_reason"));
        } else if (outcome == FailureOutcome.IGNORED) {
            log.debug("Ignoring {} {}: invoice {} attempt {} already handled or not applicable",
                INVOICE_PAYMENT_FAILED, event.id(), invoiceId, failure.attemptCount());
        } else {
            log.info("Payment failure recorded for Stripe subscription {} (invoice {}, attempt {}, event {})",
                stripeSubscriptionId, invoiceId, failure.attemptCount(), event.id());
        }
    }

    // API 2026: parent.subscription_details.subscription; older API versions: top-level subscription
    private static String invoiceSubscriptionId(JsonNode invoice) {
        String id = text(path(invoice, "parent", "subscription_details"), "subscription");
        return id != null ? id : text(invoice, "subscription");
    }

    private void handleCheckoutCompleted(WebhookEvent event) {
        JsonNode session = event.object();
        String mode = text(session, "mode");
        String paymentStatus = text(session, "payment_status");
        String clientReferenceId = text(session, "client_reference_id");
        String stripeSubscriptionId = text(session, "subscription");

        if (!"subscription".equals(mode) || stripeSubscriptionId == null || clientReferenceId == null) {
            log.warn("Ignoring {} {}: mode={}, subscription={}, client_reference_id={}",
                CHECKOUT_COMPLETED, event.id(), mode, stripeSubscriptionId, clientReferenceId);
            return;
        }
        if (!SETTLED_PAYMENT_STATUSES.contains(paymentStatus)) {
            log.warn("Ignoring {} {}: payment_status={}", CHECKOUT_COMPLETED, event.id(), paymentStatus);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(clientReferenceId);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring {} {}: client_reference_id is not a UUID", CHECKOUT_COMPLETED, event.id());
            return;
        }

        // Throws PaymentProviderUnavailableException -> 503 -> Stripe retries
        SubscriptionDetails details = stripeBillingService.retrieveSubscription(stripeSubscriptionId);
        String customerId = text(session, "customer") != null ? text(session, "customer") : details.customerId();

        try {
            subscriptionService.activateMonthly(userId, customerId, stripeSubscriptionId,
                details.paymentMethodId(), details.currentPeriodEnd());
            log.info("Monthly plan activated for user {} (event {})", userId, event.id());
        } catch (ResourceNotFoundException e) {
            // Retrying will not create the row; acknowledge so Stripe stops, but leave a trail.
            log.error("{} {} for user {} without subscription row (stripe subscription {})",
                CHECKOUT_COMPLETED, event.id(), userId, stripeSubscriptionId);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static JsonNode path(JsonNode node, String... fields) {
        JsonNode current = node;
        for (String field : fields) {
            current = current == null ? null : current.get(field);
        }
        return current;
    }

    private static JsonNode path(JsonNode array, int index, String field) {
        JsonNode item = array == null || !array.isArray() ? null : array.get(index);
        return item == null ? null : item.get(field);
    }

    // Stripe timestamps are Unix epoch seconds
    private static OffsetDateTime epochSeconds(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToLong()) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.asLong()), ZoneOffset.UTC);
    }
}
