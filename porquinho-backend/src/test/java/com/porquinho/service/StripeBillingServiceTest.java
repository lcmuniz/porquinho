package com.porquinho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.param.SubscriptionUpdateParams;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // ---- Story 2.4 ----

    @Test
    void cancelAtPeriodEndWithoutSecretKeyThrowsUnavailable() {
        assertThatThrownBy(() -> service.cancelAtPeriodEnd("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> service.cancelAtPeriodEnd(null))
            .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    private StripeBillingService serviceWithSubscriptions(com.stripe.service.SubscriptionService subscriptions) {
        StripeClient client = mock(StripeClient.class);
        when(client.subscriptions()).thenReturn(subscriptions);
        return new StripeBillingService(client, SECRET, "http://localhost:5173", new ObjectMapper());
    }

    @Test
    void cancelAtPeriodEndReturnsItemPeriodEndFromStripeResponse() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        long periodEnd = Instant.parse("2026-12-31T12:00:00Z").getEpochSecond();
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class)))
            .thenReturn(stripeSubscription(periodEnd, null));

        assertThat(serviceWithSubscriptions(subscriptions).cancelAtPeriodEnd("sub_1"))
            .isEqualTo(OffsetDateTime.of(2026, 12, 31, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void cancelAtPeriodEndFallsBackToCancelAtThenTrialEnd() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        Subscription withCancelAt = stripeSubscription(null, Instant.parse("2026-11-15T12:00:00Z").getEpochSecond());
        withCancelAt.setCancelAt(Instant.parse("2026-11-30T12:00:00Z").getEpochSecond());
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class)))
            .thenReturn(withCancelAt)
            .thenReturn(stripeSubscription(null, Instant.parse("2026-11-15T12:00:00Z").getEpochSecond()));
        StripeBillingService billing = serviceWithSubscriptions(subscriptions);

        assertThat(billing.cancelAtPeriodEnd("sub_1")).isEqualTo(OffsetDateTime.of(2026, 11, 30, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(billing.cancelAtPeriodEnd("sub_1")).isEqualTo(OffsetDateTime.of(2026, 11, 15, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void cancelAtPeriodEndWrapsStripeExceptionAsUnavailable() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class)))
            .thenThrow(new ApiConnectionException("timeout"));

        assertThatThrownBy(() -> serviceWithSubscriptions(subscriptions).cancelAtPeriodEnd("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class)
            .hasCauseInstanceOf(ApiConnectionException.class);
    }

    // ---- Story 2.5 ----

    @Test
    void resumeWithoutSecretKeyOrIdThrowsUnavailable() {
        assertThatThrownBy(() -> service.resumeSubscription("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> service.resumeSubscription(null))
            .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void resumeClearsCancelAtPeriodEndAndReturnsPeriodEnd() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        long periodEnd = Instant.parse("2026-11-30T12:00:00Z").getEpochSecond();
        org.mockito.ArgumentCaptor<SubscriptionUpdateParams> params =
            org.mockito.ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        when(subscriptions.update(eq("sub_1"), params.capture())).thenReturn(stripeSubscription(periodEnd, null));

        assertThat(serviceWithSubscriptions(subscriptions).resumeSubscription("sub_1"))
            .isEqualTo(OffsetDateTime.of(2026, 11, 30, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(params.getValue().getCancelAtPeriodEnd()).isFalse();
    }

    @Test
    void resumeWrapsStripeExceptionAsUnavailable() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class)))
            .thenThrow(new ApiConnectionException("timeout"));

        assertThatThrownBy(() -> serviceWithSubscriptions(subscriptions).resumeSubscription("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class)
            .hasCauseInstanceOf(ApiConnectionException.class);
    }

    @Test
    void resumeOfEndedSubscriptionIsConflict() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class)))
            .thenThrow(new com.stripe.exception.InvalidRequestException(
                "A canceled subscription can only update its cancellation_details and metadata.",
                null, "req_1", null, 400, null));
        Subscription ended = new Subscription();
        ended.setStatus("canceled");
        when(subscriptions.retrieve("sub_1")).thenReturn(ended);

        assertThatThrownBy(() -> serviceWithSubscriptions(subscriptions).resumeSubscription("sub_1"))
            .isInstanceOf(com.porquinho.exception.ConflictException.class)
            .hasMessageContaining("Assine novamente");
    }

    @Test
    void resumeRefusedForNotEndedSubscriptionIsUnavailable() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class)))
            .thenThrow(new com.stripe.exception.InvalidRequestException(
                "Invalid cancel_at_period_end", "cancel_at_period_end", "req_1", null, 400, null));
        Subscription active = new Subscription();
        active.setStatus("active");
        when(subscriptions.retrieve("sub_1")).thenReturn(active);

        assertThatThrownBy(() -> serviceWithSubscriptions(subscriptions).resumeSubscription("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class)
            .hasCauseInstanceOf(com.stripe.exception.InvalidRequestException.class);
    }

    @Test
    void resumeRefusedAndRetrieveFailingIsUnavailable() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        com.stripe.exception.InvalidRequestException missing = new com.stripe.exception.InvalidRequestException(
            "No such subscription: 'sub_1'", "id", "req_1", "resource_missing", 404, null);
        when(subscriptions.update(eq("sub_1"), any(SubscriptionUpdateParams.class))).thenThrow(missing);
        when(subscriptions.retrieve("sub_1")).thenThrow(missing);

        assertThatThrownBy(() -> serviceWithSubscriptions(subscriptions).resumeSubscription("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class)
            .hasCauseInstanceOf(com.stripe.exception.InvalidRequestException.class);
    }
}
