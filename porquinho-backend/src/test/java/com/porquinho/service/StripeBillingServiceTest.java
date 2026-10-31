package com.porquinho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 2.2: webhook signature verification with the real Stripe algorithm (no network).
 */
class StripeBillingServiceTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String PAYLOAD =
        "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_1\",\"mode\":\"subscription\"}}}";

    private final StripeBillingService service =
        new StripeBillingService("", SECRET, "http://localhost:5173/", new ObjectMapper());

    static String sign(String payload, String secret, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac);
    }

    @Test
    void validSignatureReturnsParsedEvent() throws Exception {
        StripeBillingService.WebhookEvent event =
            service.verifyWebhook(PAYLOAD, sign(PAYLOAD, SECRET, Instant.now().getEpochSecond()));

        assertThat(event.id()).isEqualTo("evt_1");
        assertThat(event.type()).isEqualTo("checkout.session.completed");
        assertThat(event.object().path("mode").asText()).isEqualTo("subscription");
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        String header = sign(PAYLOAD, "whsec_other", Instant.now().getEpochSecond());

        assertThatThrownBy(() -> service.verifyWebhook(PAYLOAD, header))
            .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String header = sign(PAYLOAD, SECRET, Instant.now().getEpochSecond());

        assertThatThrownBy(() -> service.verifyWebhook(PAYLOAD.replace("evt_1", "evt_2"), header))
            .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void missingHeaderIsRejected() {
        assertThatThrownBy(() -> service.verifyWebhook(PAYLOAD, null))
            .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void unconfiguredSecretRejectsEverything() throws Exception {
        StripeBillingService unconfigured = new StripeBillingService("", "", "http://x", new ObjectMapper());

        assertThatThrownBy(() -> unconfigured.verifyWebhook(PAYLOAD, sign(PAYLOAD, SECRET, Instant.now().getEpochSecond())))
            .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void checkoutWithoutSecretKeyIsUnavailable() {
        assertThatThrownBy(() -> service.createCheckoutSession(UUID.randomUUID(), "a@b.com", null))
            .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void cancelSubscriptionWithoutSecretKeyNeverThrows() {
        service.cancelSubscription("sub_1");
        service.cancelSubscription(null);
    }

    // ---- Checkout params ----

    @Test
    void sessionParamsCarryUserPriceAndReturnUrls() {
        UUID userId = UUID.randomUUID();
        Instant trialEnd = Instant.parse("2026-11-30T12:00:00Z");

        SessionCreateParams params = service.buildSessionParams(userId, "ana@example.com", trialEnd);

        assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
        assertThat(params.getClientReferenceId()).isEqualTo(userId.toString());
        assertThat(params.getCustomerEmail()).isEqualTo("ana@example.com");
        // trailing slash of app.frontend-url is stripped
        assertThat(params.getSuccessUrl()).isEqualTo("http://localhost:5173/assinatura?status=sucesso");
        assertThat(params.getCancelUrl()).isEqualTo("http://localhost:5173/assinatura?status=cancelado");
        assertThat(params.getSubscriptionData().getTrialEnd()).isEqualTo(trialEnd.getEpochSecond());

        assertThat(params.getLineItems()).hasSize(1);
        SessionCreateParams.LineItem item = params.getLineItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(1L);
        SessionCreateParams.LineItem.PriceData price = item.getPriceData();
        assertThat(price.getUnitAmount()).isEqualTo(1990L);
        assertThat(price.getCurrency()).isEqualTo("brl");
        assertThat(price.getRecurring().getInterval())
            .isEqualTo(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH);
    }

    @Test
    void sessionParamsWithoutTrialEndChargeImmediately() {
        SessionCreateParams params = service.buildSessionParams(UUID.randomUUID(), null, null);

        assertThat(params.getSubscriptionData().getTrialEnd()).isNull();
        assertThat(params.getCustomerEmail()).isNull();
    }

    // ---- Subscription mapping ----

    private static Subscription stripeSubscription(Long itemPeriodEnd, Long trialEnd) {
        SubscriptionItem item = new SubscriptionItem();
        item.setCurrentPeriodEnd(itemPeriodEnd);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        Subscription subscription = new Subscription();
        subscription.setItems(items);
        subscription.setTrialEnd(trialEnd);
        subscription.setCustomer("cus_1");
        subscription.setDefaultPaymentMethod("pm_1");
        return subscription;
    }

    @Test
    void detailsUsePeriodEndFromSubscriptionItem() {
        long periodEnd = Instant.parse("2026-12-31T12:00:00Z").getEpochSecond();

        StripeBillingService.SubscriptionDetails details =
            StripeBillingService.toDetails(stripeSubscription(periodEnd, 1L));

        assertThat(details.customerId()).isEqualTo("cus_1");
        assertThat(details.paymentMethodId()).isEqualTo("pm_1");
        assertThat(details.currentPeriodEnd()).isEqualTo(OffsetDateTime.of(2026, 12, 31, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void detailsFallBackToTrialEndWhenItemHasNoPeriodEnd() {
        long trialEnd = Instant.parse("2026-11-30T12:00:00Z").getEpochSecond();

        StripeBillingService.SubscriptionDetails details =
            StripeBillingService.toDetails(stripeSubscription(null, trialEnd));

        assertThat(details.currentPeriodEnd()).isEqualTo(OffsetDateTime.of(2026, 11, 30, 12, 0, 0, 0, ZoneOffset.UTC));
    }
}
