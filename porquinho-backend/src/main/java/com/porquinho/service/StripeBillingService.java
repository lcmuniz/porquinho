package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Thin wrapper around the Stripe SDK (Story 2.2). Everything that talks to Stripe lives here,
 * so the rest of the app (and the tests) can mock a single bean.
 *
 * Webhook payloads are verified with the signing secret and parsed with Jackson instead of the
 * SDK's typed deserializer: the typed path returns nothing when the account's API version differs
 * from the SDK's pinned version, and we only need a few fields.
 */
@Service
public class StripeBillingService {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingService.class);

    /** R$ 19,90 in centavos. Inline price: no product/price setup in the Stripe dashboard. */
    public static final long MONTHLY_PRICE_CENTS = 1990L;
    public static final String CURRENCY = "brl";
    public static final String PRODUCT_NAME = "Porquinho - Plano mensal";

    private final StripeClient client;
    private final String webhookSecret;
    private final String frontendUrl;
    private final ObjectMapper objectMapper;

    // @Autowired: with a second (test) constructor Spring would otherwise look for a no-arg one
    @Autowired
    public StripeBillingService(@Value("${stripe.secret-key:}") String secretKey,
                                @Value("${stripe.webhook-secret:}") String webhookSecret,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                ObjectMapper objectMapper) {
        this(isBlank(secretKey) ? null : new StripeClient(secretKey), webhookSecret, frontendUrl, objectMapper);
    }

    // Tests: inject a mocked StripeClient (null = Stripe not configured)
    StripeBillingService(StripeClient client, String webhookSecret, String frontendUrl, ObjectMapper objectMapper) {
        this.client = client;
        this.webhookSecret = webhookSecret;
        this.frontendUrl = stripTrailingSlash(frontendUrl);
        this.objectMapper = objectMapper;
    }

    /** Verified webhook event: id, type and the raw {@code data.object}. */
    public record WebhookEvent(String id, String type, JsonNode object) {
    }

    /** The bits of a Stripe Subscription we persist locally. */
    public record SubscriptionDetails(String customerId, String paymentMethodId, OffsetDateTime currentPeriodEnd) {
    }

    /**
     * Create a hosted Checkout session for the monthly plan and return its URL.
     *
     * @param trialEnd when non-null, Stripe holds the first charge until this instant
     *                 (user keeps the remaining trial days). Null charges immediately.
     */
    public String createCheckoutSession(UUID userId, String customerEmail, Instant trialEnd) {
        if (client == null) {
            log.error("STRIPE_SECRET_KEY not configured - cannot create Checkout session");
            throw new PaymentProviderUnavailableException("Pagamento indisponível no momento. Tente novamente mais tarde.");
        }

        try {
            Session session = client.checkout().sessions().create(buildSessionParams(userId, customerEmail, trialEnd));
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Stripe Checkout session creation failed for user {}: {}", userId, e.getMessage());
            throw new PaymentProviderUnavailableException("Pagamento indisponível no momento. Tente novamente mais tarde.", e);
        }
    }

    /** Checkout params: subscription mode, card, inline R$ 19,90/month price, return URLs. */
    SessionCreateParams buildSessionParams(UUID userId, String customerEmail, Instant trialEnd) {
        SessionCreateParams.SubscriptionData.Builder subscriptionData = SessionCreateParams.SubscriptionData.builder()
            .putMetadata("user_id", userId.toString());
        if (trialEnd != null) {
            subscriptionData.setTrialEnd(trialEnd.getEpochSecond());
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
            .setClientReferenceId(userId.toString())
            .setSuccessUrl(frontendUrl + "/assinatura?status=sucesso")
            .setCancelUrl(frontendUrl + "/assinatura?status=cancelado")
            .setLocale(SessionCreateParams.Locale.PT_BR)
            .setSubscriptionData(subscriptionData.build())
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(CURRENCY)
                    .setUnitAmount(MONTHLY_PRICE_CENTS)
                    .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                        .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                        .build())
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(PRODUCT_NAME)
                        .build())
                    .build())
                .build());
        if (!isBlank(customerEmail)) {
            params.setCustomerEmail(customerEmail);
        }
        return params.build();
    }

    /**
     * Verify the Stripe-Signature header against the raw payload and parse the event.
     *
     * @throws InvalidWebhookSignatureException header missing/invalid, secret not configured, or unparseable payload
     */
    public WebhookEvent verifyWebhook(String payload, String signatureHeader) {
        if (isBlank(webhookSecret)) {
            throw new InvalidWebhookSignatureException("Webhook secret not configured");
        }
        if (isBlank(signatureHeader) || payload == null) {
            throw new InvalidWebhookSignatureException("Missing Stripe-Signature header");
        }
        try {
            Webhook.Signature.verifyHeader(payload, signatureHeader, webhookSecret, Webhook.DEFAULT_TOLERANCE);
        } catch (SignatureVerificationException e) {
            throw new InvalidWebhookSignatureException("Invalid Stripe-Signature", e);
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            return new WebhookEvent(
                root.path("id").asText(null),
                root.path("type").asText(null),
                root.path("data").path("object"));
        } catch (IOException e) {
            throw new InvalidWebhookSignatureException("Unparseable webhook payload", e);
        }
    }

    /**
     * Fetch a Stripe Subscription for its period end and default payment method.
     *
     * @throws PaymentProviderUnavailableException Stripe call failed (webhook answers 5xx so Stripe retries)
     */
    public SubscriptionDetails retrieveSubscription(String stripeSubscriptionId) {
        if (client == null) {
            throw new PaymentProviderUnavailableException("STRIPE_SECRET_KEY not configured");
        }
        try {
            return toDetails(client.subscriptions().retrieve(stripeSubscriptionId));
        } catch (StripeException e) {
            log.error("Stripe subscription retrieve failed for {}: {}", stripeSubscriptionId, e.getMessage());
            throw new PaymentProviderUnavailableException("Stripe unavailable", e);
        }
    }

    /**
     * Cancel a Stripe subscription immediately. Best-effort: never throws (like SupabaseAdminClient);
     * failures are logged so they can be cancelled by hand in the Stripe dashboard.
     */
    public void cancelSubscription(String stripeSubscriptionId) {
        if (isBlank(stripeSubscriptionId)) {
            return;
        }
        if (client == null) {
            log.warn("STRIPE_SECRET_KEY not configured - skipping cancel of Stripe subscription {}", stripeSubscriptionId);
            return;
        }
        try {
            client.subscriptions().cancel(stripeSubscriptionId);
            log.info("Stripe subscription {} cancelled", stripeSubscriptionId);
        } catch (Exception e) {
            log.warn("Failed to cancel Stripe subscription {}: {} - {}",
                stripeSubscriptionId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Schedule a Stripe subscription to end at the current period end (Story 2.4 self-service cancel).
     * No further charge; Stripe sends customer.subscription.deleted when the period is over.
     * Unlike {@link #cancelSubscription}, this is synchronous and NOT best-effort: the caller only
     * marks the local row CANCELLED after Stripe accepted.
     *
     * @return end of the current period per Stripe's response (when access ends): item current_period_end,
     *         else cancel_at, else trial_end; null if Stripe returned none
     * @throws PaymentProviderUnavailableException Stripe not configured or the call failed
     */
    public OffsetDateTime cancelAtPeriodEnd(String stripeSubscriptionId) {
        if (isBlank(stripeSubscriptionId)) {
            throw new PaymentProviderUnavailableException("No Stripe subscription id to cancel");
        }
        if (client == null) {
            log.warn("STRIPE_SECRET_KEY not configured - cannot cancel Stripe subscription {}", stripeSubscriptionId);
            throw new PaymentProviderUnavailableException("STRIPE_SECRET_KEY not configured");
        }
        try {
            Subscription updated = client.subscriptions().update(stripeSubscriptionId,
                SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
            log.info("Stripe subscription {} scheduled to cancel at period end", stripeSubscriptionId);
            return toOffsetDateTime(periodEnd(updated));
        } catch (StripeException e) {
            log.warn("Failed to schedule cancel of Stripe subscription {}: {}", stripeSubscriptionId, e.getMessage());
            throw new PaymentProviderUnavailableException("Stripe unavailable", e);
        }
    }

    /**
     * Undo a scheduled cancel (Story 2.5): {@code cancel_at_period_end=false}. No charge now; the next
     * charge happens on the date Stripe already had. Synchronous, NOT best-effort: the caller only
     * marks the local row ACTIVE after Stripe accepted.
     *
     * @return end of the current period (next charge) per Stripe's response; null if Stripe returned none
     * @throws ConflictException Stripe refused (e.g. the subscription already ended): subscribe again
     * @throws PaymentProviderUnavailableException Stripe not configured or the call failed
     */
    public OffsetDateTime resumeSubscription(String stripeSubscriptionId) {
        if (isBlank(stripeSubscriptionId)) {
            throw new PaymentProviderUnavailableException("No Stripe subscription id to resume");
        }
        if (client == null) {
            log.warn("STRIPE_SECRET_KEY not configured - cannot resume Stripe subscription {}", stripeSubscriptionId);
            throw new PaymentProviderUnavailableException("STRIPE_SECRET_KEY not configured");
        }
        try {
            Subscription updated = client.subscriptions().update(stripeSubscriptionId,
                SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(false).build());
            log.info("Stripe subscription {} resumed (scheduled cancel removed)", stripeSubscriptionId);
            return toOffsetDateTime(periodEnd(updated));
        } catch (InvalidRequestException e) {
            // Only an ended subscription is "subscribe again"; a wrong id, bad param or key mismatch is ours
            String status = null;
            try {
                status = client.subscriptions().retrieve(stripeSubscriptionId).getStatus();
            } catch (Exception retrieveFailure) {
                log.error("Could not retrieve Stripe subscription {} after resume was refused: {}",
                    stripeSubscriptionId, retrieveFailure.getMessage());
            }
            if ("canceled".equals(status)) {
                log.warn("Stripe refused to resume ended subscription {}: {}", stripeSubscriptionId, e.getMessage());
                throw new ConflictException("Sua assinatura já terminou. Assine novamente para reativar.");
            }
            log.error("Stripe refused to resume subscription {} (status {}): {}",
                stripeSubscriptionId, status, e.getMessage());
            throw new PaymentProviderUnavailableException("Stripe refused resume", e);
        } catch (StripeException e) {
            log.warn("Failed to resume Stripe subscription {}: {}", stripeSubscriptionId, e.getMessage());
            throw new PaymentProviderUnavailableException("Stripe unavailable", e);
        }
    }

    static SubscriptionDetails toDetails(Subscription subscription) {
        return new SubscriptionDetails(
            subscription.getCustomer(),
            subscription.getDefaultPaymentMethod(),
            toOffsetDateTime(currentPeriodEnd(subscription)));
    }

    // Since API 2025-03-31 current_period_end lives on the subscription item, not the subscription.
    private static Long currentPeriodEnd(Subscription subscription) {
        Long itemEnd = itemPeriodEnd(subscription);
        return itemEnd != null ? itemEnd : subscription.getTrialEnd();
    }

    // Same as currentPeriodEnd, but a scheduled cancel_at wins over trial_end (Story 2.4)
    static Long periodEnd(Subscription subscription) {
        Long itemEnd = itemPeriodEnd(subscription);
        if (itemEnd != null) {
            return itemEnd;
        }
        return subscription.getCancelAt() != null ? subscription.getCancelAt() : subscription.getTrialEnd();
    }

    private static Long itemPeriodEnd(Subscription subscription) {
        if (subscription.getItems() != null && subscription.getItems().getData() != null) {
            for (SubscriptionItem item : subscription.getItems().getData()) {
                if (item.getCurrentPeriodEnd() != null) {
                    return item.getCurrentPeriodEnd();
                }
            }
        }
        return null;
    }

    private static OffsetDateTime toOffsetDateTime(Long epochSeconds) {
        return epochSeconds == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
