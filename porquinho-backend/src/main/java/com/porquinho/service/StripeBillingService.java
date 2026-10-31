package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public StripeBillingService(@Value("${stripe.secret-key:}") String secretKey,
                                @Value("${stripe.webhook-secret:}") String webhookSecret,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                ObjectMapper objectMapper) {
        this.client = isBlank(secretKey) ? null : new StripeClient(secretKey);
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

    static SubscriptionDetails toDetails(Subscription subscription) {
        return new SubscriptionDetails(
            subscription.getCustomer(),
            subscription.getDefaultPaymentMethod(),
            toOffsetDateTime(currentPeriodEnd(subscription)));
    }

    // Since API 2025-03-31 current_period_end lives on the subscription item, not the subscription.
    private static Long currentPeriodEnd(Subscription subscription) {
        if (subscription.getItems() != null && subscription.getItems().getData() != null) {
            for (SubscriptionItem item : subscription.getItems().getData()) {
                if (item.getCurrentPeriodEnd() != null) {
                    return item.getCurrentPeriodEnd();
                }
            }
        }
        return subscription.getTrialEnd();
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
