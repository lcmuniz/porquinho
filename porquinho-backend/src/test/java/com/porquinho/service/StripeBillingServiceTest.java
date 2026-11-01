package com.porquinho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.stripe.model.Invoice;
import com.stripe.model.InvoicePayment;
import com.stripe.model.InvoicePaymentCollection;
import com.stripe.model.Refund;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.net.RequestOptions;
import com.stripe.param.InvoiceRetrieveParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.service.InvoiceService;
import com.stripe.service.RefundService;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    // Stripe services behind the refund tests (retro Epic 2 F2). SubscriptionService stays fully qualified:
    // the simple name is com.porquinho.service.SubscriptionService in this package.
    private final com.stripe.service.SubscriptionService refundSubscriptions =
        mock(com.stripe.service.SubscriptionService.class);
    private final InvoiceService refundInvoices = mock(InvoiceService.class);
    private final RefundService refunds = mock(RefundService.class);

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
        subscription.setStatus("active");
        return subscription;
    }

    @Test
    void detailsUsePeriodEndFromSubscriptionItem() {
        long periodEnd = Instant.parse("2026-12-31T12:00:00Z").getEpochSecond();

        StripeBillingService.SubscriptionDetails details =
            StripeBillingService.toDetails(stripeSubscription(periodEnd, 1L));

        assertThat(details.customerId()).isEqualTo("cus_1");
        assertThat(details.paymentMethodId()).isEqualTo("pm_1");
        assertThat(details.status()).isEqualTo("active");
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

    // ---- Story 2.7 ----

    @Test
    void cancelImmediatelyWithoutSecretKeyOrIdThrowsUnavailable() {
        assertThatThrownBy(() -> service.cancelImmediately("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> service.cancelImmediately(null))
            .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void cancelImmediatelyCancelsTheStripeSubscription() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        Subscription cancelled = new Subscription();
        cancelled.setLatestInvoice("in_open");
        when(subscriptions.cancel("sub_1")).thenReturn(cancelled).thenReturn(new Subscription()).thenReturn(null);
        StripeBillingService billing = serviceWithSubscriptions(subscriptions);

        assertThat(billing.cancelImmediately("sub_1")).isEqualTo("in_open");
        assertThat(billing.cancelImmediately("sub_1")).isNull();
        assertThat(billing.cancelImmediately("sub_1")).isNull();
        org.mockito.Mockito.verify(subscriptions, org.mockito.Mockito.times(3)).cancel("sub_1");
    }

    @Test
    void cancelImmediatelyWrapsStripeExceptionAsUnavailable() throws Exception {
        com.stripe.service.SubscriptionService subscriptions = mock(com.stripe.service.SubscriptionService.class);
        when(subscriptions.cancel("sub_1")).thenThrow(new ApiConnectionException("timeout"));

        assertThatThrownBy(() -> serviceWithSubscriptions(subscriptions).cancelImmediately("sub_1"))
            .isInstanceOf(PaymentProviderUnavailableException.class)
            .hasCauseInstanceOf(ApiConnectionException.class);
    }

    private StripeBillingService serviceWithInvoices(com.stripe.service.InvoiceService invoices) {
        StripeClient client = mock(StripeClient.class);
        when(client.invoices()).thenReturn(invoices);
        return new StripeBillingService(client, SECRET, "http://localhost:5173", new ObjectMapper());
    }

    @Test
    void voidInvoiceVoidsTheStripeInvoice() throws Exception {
        com.stripe.service.InvoiceService invoices = mock(com.stripe.service.InvoiceService.class);

        serviceWithInvoices(invoices).voidInvoice("in_open");

        org.mockito.Mockito.verify(invoices).voidInvoice("in_open");
    }

    @Test
    void voidInvoiceIsBestEffortAndNeverThrows() throws Exception {
        com.stripe.service.InvoiceService invoices = mock(com.stripe.service.InvoiceService.class);
        when(invoices.voidInvoice("in_open")).thenThrow(new ApiConnectionException("timeout"));
        StripeBillingService billing = serviceWithInvoices(invoices);

        billing.voidInvoice("in_open");
        billing.voidInvoice(null);
        service.voidInvoice("in_open");

        org.mockito.Mockito.verify(invoices).voidInvoice("in_open");
    }

    // ---- Retro Epic 2 F2: cancel and refund a duplicate Checkout / deleted user's subscription ----

    private StripeBillingService serviceForRefund() throws Exception {
        StripeClient client = mock(StripeClient.class);
        when(client.subscriptions()).thenReturn(refundSubscriptions);
        when(client.invoices()).thenReturn(refundInvoices);
        when(client.refunds()).thenReturn(refunds);
        Subscription subscription = new Subscription();
        subscription.setLatestInvoice("in_dup");
        when(refundSubscriptions.retrieve("sub_dup")).thenReturn(subscription);
        return new StripeBillingService(client, SECRET, "http://localhost:5173", new ObjectMapper());
    }

    private static Invoice invoicePaying(long amountPaid, String paymentStatus, String paymentIntentId) {
        InvoicePayment.Payment payment = new InvoicePayment.Payment();
        payment.setType("payment_intent");
        payment.setPaymentIntent(paymentIntentId);
        InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setStatus(paymentStatus);
        invoicePayment.setPayment(payment);
        InvoicePaymentCollection payments = new InvoicePaymentCollection();
        payments.setData(List.of(invoicePayment));
        Invoice invoice = new Invoice();
        invoice.setId("in_dup");
        invoice.setAmountPaid(amountPaid);
        invoice.setPayments(payments);
        return invoice;
    }

    @Test
    void refundLatestInvoiceRefundsThePaymentIntentOfTheExpandedPaymentsWithTheGivenReason() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundInvoices.retrieve(eq("in_dup"), any(InvoiceRetrieveParams.class)))
            .thenReturn(invoicePaying(1990, "paid", "pi_dup"));
        when(refunds.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(new Refund());

        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE);

        ArgumentCaptor<InvoiceRetrieveParams> retrieve = ArgumentCaptor.forClass(InvoiceRetrieveParams.class);
        verify(refundInvoices).retrieve(eq("in_dup"), retrieve.capture());
        assertThat(retrieve.getValue().getExpand()).containsExactly("payments");
        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(refunds).create(params.capture(), options.capture());
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_dup");
        assertThat(params.getValue().getAmount()).isNull(); // full refund
        assertThat(params.getValue().getReason()).isEqualTo(RefundCreateParams.Reason.DUPLICATE);
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("refund-pi_dup");
    }

    @Test
    void refundLatestInvoiceForDeletedUserUsesRequestedByCustomer() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundInvoices.retrieve(eq("in_dup"), any(InvoiceRetrieveParams.class)))
            .thenReturn(invoicePaying(1990, "paid", "pi_dup"));

        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        verify(refunds).create(params.capture(), any(RequestOptions.class));
        assertThat(params.getValue().getReason()).isEqualTo(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
    }

    @Test
    void refundLatestInvoiceOfZeroInvoiceRefundsNothing() throws Exception {
        // Duplicate during the trial: R$ 0 invoice
        StripeBillingService billing = serviceForRefund();
        when(refundInvoices.retrieve(eq("in_dup"), any(InvoiceRetrieveParams.class)))
            .thenReturn(invoicePaying(0, "paid", null));

        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE);

        verifyNoInteractions(refunds);
    }

    @Test
    void refundLatestInvoiceSkipsPaymentsNotPaidOrWithoutPaymentIntent() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundInvoices.retrieve(eq("in_dup"), any(InvoiceRetrieveParams.class)))
            .thenReturn(invoicePaying(1990, "open", "pi_dup"));

        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE);

        verifyNoInteractions(refunds);
    }

    @Test
    void refundLatestInvoiceIsBestEffortAndNeverThrows() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundInvoices.retrieve(eq("in_dup"), any(InvoiceRetrieveParams.class)))
            .thenReturn(invoicePaying(1990, "paid", "pi_dup"));
        when(refunds.create(any(RefundCreateParams.class), any(RequestOptions.class)))
            .thenThrow(new ApiConnectionException("timeout"));

        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE);
        billing.refundLatestInvoice(null, RefundCreateParams.Reason.DUPLICATE);
        service.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE); // Stripe not configured

        when(refundSubscriptions.retrieve("sub_dup")).thenThrow(new ApiConnectionException("down"));
        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE);
    }

    @Test
    void refundLatestInvoiceWithoutInvoiceRefundsNothing() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundSubscriptions.retrieve("sub_dup")).thenReturn(new Subscription());

        billing.refundLatestInvoice("sub_dup", RefundCreateParams.Reason.DUPLICATE);

        verifyNoInteractions(refundInvoices, refunds);
    }

    @Test
    void cancelSubscriptionReturnsWhetherStripeCancelled() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundSubscriptions.cancel("sub_fail")).thenThrow(new ApiConnectionException("down"));

        assertThat(billing.cancelSubscription("sub_dup")).isTrue();
        assertThat(billing.cancelSubscription("sub_fail")).isFalse();
        assertThat(billing.cancelSubscription(null)).isFalse();
        assertThat(service.cancelSubscription("sub_dup")).isFalse(); // Stripe not configured
    }

    @Test
    void cancelAndRefundRefundsOnlyAfterStripeCancelled() throws Exception {
        StripeBillingService billing = serviceForRefund();
        when(refundInvoices.retrieve(eq("in_dup"), any(InvoiceRetrieveParams.class)))
            .thenReturn(invoicePaying(1990, "paid", "pi_dup"));

        billing.cancelAndRefund("sub_dup", RefundCreateParams.Reason.DUPLICATE);

        InOrder stripe = inOrder(refundSubscriptions, refunds);
        stripe.verify(refundSubscriptions).cancel("sub_dup");
        stripe.verify(refunds).create(any(RefundCreateParams.class), any(RequestOptions.class));
    }

    @Test
    void cancelAndRefundDoesNotRefundWhenCancelFailed() throws Exception {
        // A refunded but still alive subscription would keep billing monthly: leave both steps for a manual fix
        StripeBillingService billing = serviceForRefund();
        when(refundSubscriptions.cancel("sub_dup")).thenThrow(new ApiConnectionException("down"));

        billing.cancelAndRefund("sub_dup", RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
        service.cancelAndRefund("sub_dup", RefundCreateParams.Reason.DUPLICATE); // Stripe not configured

        verify(refundSubscriptions).cancel("sub_dup");
        verifyNoInteractions(refundInvoices, refunds);
    }
}
