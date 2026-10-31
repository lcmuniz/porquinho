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
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", periodEnd));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "sig"))
            .andExpect(status().isOk());

        verify(subscriptionService).activateMonthly(USER_ID, "cus_1", "sub_1", "pm_1", periodEnd);
    }

    @Test
    void subscribingDuringTrialWithNoPaymentRequiredActivates() throws Exception {
        OffsetDateTime trialEnd = OffsetDateTime.parse("2026-11-30T12:00:00Z");
        when(stripeBillingService.verifyWebhook(anyString(), anyString())).thenReturn(checkoutCompleted("no_payment_required"));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", trialEnd));

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
            .thenReturn(new WebhookEvent("evt_2", "invoice.paid", JSON.readTree("{}")));

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
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", OffsetDateTime.parse("2026-11-30T12:00:00Z")));
        doThrow(new ResourceNotFoundException("Subscription not found"))
            .when(subscriptionService).activateMonthly(any(), any(), any(), any(), any());

        postWebhook();
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
            .thenReturn(new SubscriptionDetails("cus_other", "pm_1", periodEnd));

        postWebhook();

        verify(subscriptionService).activateMonthly(USER_ID, "cus_1", "sub_1", "pm_1", periodEnd);
    }
}
