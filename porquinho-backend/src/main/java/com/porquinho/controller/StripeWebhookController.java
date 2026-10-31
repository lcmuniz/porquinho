package com.porquinho.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.StripeBillingService;
import com.porquinho.service.StripeBillingService.SubscriptionDetails;
import com.porquinho.service.StripeBillingService.WebhookEvent;
import com.porquinho.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Stripe webhook (Story 2.2). Public in SecurityConfig: trust comes from the Stripe-Signature
 * header, verified against STRIPE_WEBHOOK_SECRET. Only checkout.session.completed is handled;
 * other events get 200 so Stripe stops retrying them.
 *
 * Errors: bad signature -> 400 (nothing changes); Stripe API down while fetching the
 * subscription -> 503 (Stripe retries the delivery).
 */
@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    static final String CHECKOUT_COMPLETED = "checkout.session.completed";

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

        if (!CHECKOUT_COMPLETED.equals(event.type())) {
            log.debug("Ignoring Stripe event {} ({})", event.id(), event.type());
            return ResponseEntity.ok().build();
        }

        handleCheckoutCompleted(event);
        return ResponseEntity.ok().build();
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
}
