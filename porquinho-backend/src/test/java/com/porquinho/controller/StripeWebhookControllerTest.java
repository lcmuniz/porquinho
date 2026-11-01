package com.porquinho.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.exception.InvalidWebhookSignatureException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.StripeBillingService;
import com.porquinho.service.StripeBillingService.SubscriptionDetails;
import com.porquinho.service.StripeBillingService.WebhookEvent;
import com.porquinho.service.SubscriptionService;
import com.porquinho.service.SubscriptionService.FailureOutcome;
import com.porquinho.service.SubscriptionService.InvoicePayment;
import com.porquinho.service.SubscriptionService.PaymentFailure;
import com.porquinho.service.SubscriptionService.PaymentOutcome;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeWebhookController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class StripeWebhookControllerTest {

    private static final String URL = "/api/v1/webhooks/stripe";
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeBillingService stripeBillingService;

    @MockBean
    private SubscriptionService subscriptionService;

    private WebhookEvent checkoutCompleted(String paymentStatus) throws Exception {
        return new WebhookEvent("evt_1", "checkout.session.completed", JSON.readTree(
            "{\"mode\":\"subscription\",\"payment_status\":\"" + paymentStatus + "\","
                + "\"client_reference_id\":\"" + USER_ID + "\",\"customer\":\"cus_1\",\"subscription\":\"sub_1\"}"));
    }

    @Test
    void invalidSignatureReturns400AndChangesNothing() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), any()))
            .thenThrow(new InvalidWebhookSignatureException("Invalid Stripe-Signature"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "t=1,v1=bad"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void validCheckoutCompletedActivatesMonthlyPlan() throws Exception {
        OffsetDateTime periodEnd = OffsetDateTime.parse("2026-11-30T12:00:00Z");
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("paid"));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", periodEnd, "active"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isOk());

        verify(subscriptionService).activateMonthly(USER_ID, "cus_1", "sub_1", "pm_1", periodEnd);
    }

    @Test
    void subscribingDuringTrialWithNoPaymentRequiredActivates() throws Exception {
        OffsetDateTime trialEnd = OffsetDateTime.parse("2026-11-30T12:00:00Z");
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("no_payment_required"));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", trialEnd, "active"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isOk());

        verify(subscriptionService).activateMonthly(USER_ID, "cus_1", "sub_1", "pm_1", trialEnd);
    }

    @Test
    void unpaidCheckoutIsIgnored() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("unpaid"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isOk());

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void otherEventTypesAreAcknowledgedAndIgnored() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString()))
            .thenReturn(new WebhookEvent("evt_2", "invoice.created", JSON.readTree("{}")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isOk());

        verifyNoInteractions(subscriptionService);
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    @Test
    void stripeDownWhileFetchingSubscriptionReturns503SoStripeRetries() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("paid"));
        when(stripeBillingService.retrieveSubscription("sub_1")).thenThrow(new PaymentProviderUnavailableException("down"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(subscriptionService);
    }

    private WebhookEvent session(String json) throws Exception {
        return new WebhookEvent("evt_3", "checkout.session.completed", JSON.readTree(json));
    }

    private void postWebhook() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isOk());
    }

    @Test
    void unknownUserIsAcknowledgedWith200() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("paid"));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", OffsetDateTime.parse("2026-11-30T12:00:00Z"), "active"));
        doThrow(new ResourceNotFoundException("Subscription not found"))
            .when(subscriptionService).activateMonthly(any(), any(), any(), any(), any());

        postWebhook();

        // Purged user (retro Epic 2 F2): the new Stripe subscription is cancelled and its charge refunded
        verify(stripeBillingService).cancelAndRefund("sub_1", RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
    }

    @Test
    void paymentModeSessionIsIgnored() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(session(
            "{\"mode\":\"payment\",\"payment_status\":\"paid\",\"client_reference_id\":\"" + USER_ID + "\",\"subscription\":\"sub_1\"}"));

        postWebhook();

        verifyNoInteractions(subscriptionService);
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    @Test
    void nonUuidClientReferenceIsIgnored() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(session(
            "{\"mode\":\"subscription\",\"payment_status\":\"paid\",\"client_reference_id\":\"not-a-uuid\",\"subscription\":\"sub_1\"}"));

        postWebhook();

        verifyNoInteractions(subscriptionService);
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    @Test
    void sessionCustomerWinsOverSubscriptionDetailsCustomer() throws Exception {
        OffsetDateTime periodEnd = OffsetDateTime.parse("2026-11-30T12:00:00Z");
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("paid"));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_other", "pm_1", periodEnd, "active"));

        postWebhook();

        verify(subscriptionService).activateMonthly(USER_ID, "cus_1", "sub_1", "pm_1", periodEnd);
    }

    // ---- Story 2.4 ----

    @Test
    void subscriptionDeletedExpiresLocalSubscription() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(new WebhookEvent(
            "evt_4", "customer.subscription.deleted", JSON.readTree("{\"id\":\"sub_1\",\"object\":\"subscription\"}")));
        when(subscriptionService.expireByStripeSubscriptionId("sub_1", null)).thenReturn(true);

        postWebhook();

        verify(subscriptionService).expireByStripeSubscriptionId("sub_1", null);
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    @Test
    void subscriptionDeletedForUnknownIdIsAcknowledgedWith200() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(new WebhookEvent(
            "evt_5", "customer.subscription.deleted", JSON.readTree("{\"id\":\"sub_unknown\"}")));
        when(subscriptionService.expireByStripeSubscriptionId("sub_unknown", null)).thenReturn(false);

        postWebhook();

        verify(subscriptionService).expireByStripeSubscriptionId("sub_unknown", null);
    }

    @Test
    void subscriptionDeletedPassesLatestInvoiceIdForTheVoidOnSuspension() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(new WebhookEvent(
            "evt_4b", "customer.subscription.deleted",
            JSON.readTree("{\"id\":\"sub_1\",\"latest_invoice\":\"in_open\"}")));

        postWebhook();

        verify(subscriptionService).expireByStripeSubscriptionId("sub_1", "in_open");
    }

    @Test
    void subscriptionDeletedReadsExpandedLatestInvoice() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(new WebhookEvent(
            "evt_4c", "customer.subscription.deleted",
            JSON.readTree("{\"id\":\"sub_1\",\"latest_invoice\":{\"id\":\"in_open\",\"object\":\"invoice\"}}")));

        postWebhook();

        verify(subscriptionService).expireByStripeSubscriptionId("sub_1", "in_open");
    }

    @Test
    void subscriptionDeletedWithoutIdIsIgnored() throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(new WebhookEvent(
            "evt_6", "customer.subscription.deleted", JSON.readTree("{}")));

        postWebhook();

        verifyNoInteractions(subscriptionService);
    }

    // ---- Story 2.6: invoice.paid ----

    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2026-11-30T12:00:05Z");
    private static final OffsetDateTime PERIOD_END = OffsetDateTime.parse("2026-12-30T12:00:00Z");

    private void givenInvoicePaid(String json) throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString()))
            .thenReturn(new WebhookEvent("evt_7", "invoice.paid", JSON.readTree(json)));
    }

    // API 2026 shape: subscription id under parent.subscription_details
    private static String invoiceJson(String billingReason, long amountPaid) {
        return "{\"id\":\"in_1\",\"object\":\"invoice\",\"amount_paid\":" + amountPaid + ",\"currency\":\"brl\","
            + "\"billing_reason\":\"" + billingReason + "\","
            + "\"parent\":{\"type\":\"subscription_details\",\"subscription_details\":{\"subscription\":\"sub_1\"}},"
            + "\"status_transitions\":{\"paid_at\":" + PAID_AT.toEpochSecond() + "},"
            + "\"lines\":{\"data\":[{\"period\":{\"start\":1,\"end\":" + PERIOD_END.toEpochSecond() + "}}]}}";
    }

    private static final InvoicePayment EXPECTED_PAYMENT =
        new InvoicePayment("in_1", "sub_1", 1990, "brl", PAID_AT, PERIOD_END);

    private void postWebhookExpecting(int status) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().is(status));
    }

    @Test
    void invoicePaidRecordsRenewalPayment() throws Exception {
        givenInvoicePaid(invoiceJson("subscription_cycle", 1990));
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.RECORDED);

        postWebhook();

        verify(subscriptionService).recordPayment(EXPECTED_PAYMENT);
        // The period is fetched by the service (with fallback), not by the controller
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    @Test
    void invoicePaidReadsLegacyTopLevelSubscriptionField() throws Exception {
        givenInvoicePaid("{\"id\":\"in_1\",\"subscription\":\"sub_1\",\"amount_paid\":1990,\"currency\":\"brl\","
            + "\"billing_reason\":\"subscription_cycle\","
            + "\"status_transitions\":{\"paid_at\":" + PAID_AT.toEpochSecond() + "},"
            + "\"lines\":{\"data\":[{\"period\":{\"end\":" + PERIOD_END.toEpochSecond() + "}}]}}");
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.RECORDED);

        postWebhook();

        verify(subscriptionService).recordPayment(EXPECTED_PAYMENT);
    }

    @Test
    void invoicePaidWithoutPaidAtOrLinesPassesNulls() throws Exception {
        givenInvoicePaid("{\"id\":\"in_1\",\"subscription\":\"sub_1\",\"amount_paid\":1990,\"currency\":\"brl\","
            + "\"status_transitions\":{\"paid_at\":null},\"lines\":{\"data\":[]}}");
        InvoicePayment expected = new InvoicePayment("in_1", "sub_1", 1990, "brl", null, null);
        when(subscriptionService.recordPayment(expected)).thenReturn(PaymentOutcome.RECORDED);

        postWebhook();

        verify(subscriptionService).recordPayment(expected);
    }

    @Test
    void zeroAmountInvoiceIsIgnored() throws Exception {
        // Subscribed during the trial: Stripe issues a R$ 0 invoice on subscription_create
        givenInvoicePaid(invoiceJson("subscription_create", 0));

        postWebhook();

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void invoiceWithoutSubscriptionIsIgnored() throws Exception {
        givenInvoicePaid("{\"id\":\"in_1\",\"amount_paid\":5000,\"currency\":\"brl\",\"parent\":null}");

        postWebhook();

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void invoiceWithoutIdIsIgnored() throws Exception {
        givenInvoicePaid("{\"subscription\":\"sub_1\",\"amount_paid\":1990,\"currency\":\"brl\"}");

        postWebhook();

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void replayedInvoiceIsAcknowledgedWith200() throws Exception {
        givenInvoicePaid(invoiceJson("subscription_cycle", 1990));
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.DUPLICATE);

        postWebhook();

        verify(subscriptionService).recordPayment(EXPECTED_PAYMENT);
    }

    @Test
    void firstInvoiceBeforeCheckoutReturns503SoStripeRetries() throws Exception {
        givenInvoicePaid(invoiceJson("subscription_create", 1990));
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.UNKNOWN_SUBSCRIPTION);
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", PERIOD_END, "active"));

        postWebhookExpecting(503);
    }

    @Test
    void firstInvoiceOfCancelledDuplicateIsAcknowledgedWith200() throws Exception {
        // Retro Epic 2 F2: the duplicate Checkout's subscription was cancelled on checkout.session.completed
        givenInvoicePaid(invoiceJson("subscription_create", 1990));
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.UNKNOWN_SUBSCRIPTION);
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", PERIOD_END, "canceled"));

        postWebhook();

        verify(subscriptionService).recordPayment(EXPECTED_PAYMENT);
        verify(subscriptionService, never()).activateMonthly(any(), any(), any(), any(), any());
        verify(stripeBillingService, never()).cancelAndRefund(anyString(), any());
    }

    @Test
    void stripeDownWhileCheckingUnknownFirstInvoiceReturns503() throws Exception {
        givenInvoicePaid(invoiceJson("subscription_create", 1990));
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.UNKNOWN_SUBSCRIPTION);
        when(stripeBillingService.retrieveSubscription("sub_1")).thenThrow(new PaymentProviderUnavailableException("down"));

        postWebhookExpecting(503);
    }

    @Test
    void unknownSubscriptionForOtherBillingReasonIsAcknowledgedWith200() throws Exception {
        givenInvoicePaid(invoiceJson("subscription_cycle", 1990));
        when(subscriptionService.recordPayment(EXPECTED_PAYMENT)).thenReturn(PaymentOutcome.UNKNOWN_SUBSCRIPTION);

        postWebhook();

        verify(subscriptionService).recordPayment(EXPECTED_PAYMENT);
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    // ---- Story 2.7: invoice.payment_failed ----

    private void givenPaymentFailed(String invoiceJson) throws Exception {
        when(stripeBillingService.verifyWebhook(anyString(), anyString()))
            .thenReturn(new WebhookEvent("evt_f", "invoice.payment_failed", JSON.readTree(invoiceJson)));
    }

    private static final String FAILED_INVOICE = "{\"id\":\"in_f\",\"billing_reason\":\"subscription_cycle\","
        + "\"attempt_count\":2,\"hosted_invoice_url\":\"https://invoice.stripe.com/i/in_f\","
        + "\"next_payment_attempt\":1793000000,"
        + "\"last_finalization_error\":{\"message\":\"Your card was declined.\"},"
        + "\"parent\":{\"subscription_details\":{\"subscription\":\"sub_1\"}}}";

    @Test
    void paymentFailedRecordsFailureWithAttemptAndLink() throws Exception {
        // last_finalization_error is not a decline reason and is not read (decision 3A)
        givenPaymentFailed(FAILED_INVOICE);
        PaymentFailure expected = new PaymentFailure("in_f", "sub_1", 2, "https://invoice.stripe.com/i/in_f", true);
        when(subscriptionService.recordPaymentFailure(expected)).thenReturn(FailureOutcome.RECORDED);

        postWebhook();

        verify(subscriptionService).recordPaymentFailure(expected);
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
    }

    @Test
    void paymentFailedReadsTopLevelSubscription() throws Exception {
        givenPaymentFailed("{\"id\":\"in_f\",\"attempt_count\":1,\"subscription\":\"sub_1\","
            + "\"hosted_invoice_url\":\"https://invoice.stripe.com/i/in_f\",\"next_payment_attempt\":null}");
        // next_payment_attempt null: last attempt, Stripe will not retry
        PaymentFailure expected = new PaymentFailure("in_f", "sub_1", 1, "https://invoice.stripe.com/i/in_f", false);
        when(subscriptionService.recordPaymentFailure(expected)).thenReturn(FailureOutcome.IGNORED);

        postWebhook();

        verify(subscriptionService).recordPaymentFailure(expected);
    }

    @Test
    void paymentFailedOfUnknownSubscriptionIsAcknowledgedWith200() throws Exception {
        // First invoice declined inside Checkout: the subscription id is not known locally yet
        givenPaymentFailed("{\"id\":\"in_f\",\"billing_reason\":\"subscription_create\",\"attempt_count\":1,"
            + "\"parent\":{\"subscription_details\":{\"subscription\":\"sub_new\"}}}");
        when(subscriptionService.recordPaymentFailure(any())).thenReturn(FailureOutcome.UNKNOWN_SUBSCRIPTION);

        postWebhook();

        verify(subscriptionService).recordPaymentFailure(new PaymentFailure("in_f", "sub_new", 1, null, false));
    }

    @Test
    void paymentFailedWithoutInvoiceOrSubscriptionIsIgnored() throws Exception {
        givenPaymentFailed("{\"attempt_count\":1,\"subscription\":\"sub_1\"}");
        postWebhook();
        givenPaymentFailed("{\"id\":\"in_f\",\"attempt_count\":1}");
        postWebhook();

        verifyNoInteractions(subscriptionService);
    }
}
