package com.porquinho.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.Subscription;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    @Test
    void getMySubscriptionShouldReturn200WithBody() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-31T12:00:00Z");
        Subscription sub = new Subscription(userId, Subscription.PlanType.TRIAL, Subscription.Status.ACTIVE,
            start, start.plusDays(30));
        when(subscriptionService.findByUserId(userId)).thenReturn(Optional.of(sub));
        when(subscriptionService.daysRemaining(sub)).thenReturn(30L);
        when(subscriptionService.hasAccess(sub)).thenReturn(true);

        mockMvc.perform(get("/api/v1/subscriptions/me").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.planType").value("TRIAL"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.trialStartDate").exists())
            .andExpect(jsonPath("$.trialEndDate").exists())
            .andExpect(jsonPath("$.daysRemaining").value(30))
            .andExpect(jsonPath("$.hasAccess").value(true));
    }

    @Test
    void getMySubscriptionShouldReturn404WhenNone() throws Exception {
        when(subscriptionService.findByUserId(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/subscriptions/me").with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }

    @Test
    void checkoutShouldReturn200WithStripeUrl() throws Exception {
        when(subscriptionService.createCheckout(userId)).thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");

        mockMvc.perform(post("/api/v1/subscriptions/checkout").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/cs_test_1"));
    }

    @Test
    void checkoutShouldReturn409WhenAlreadyMonthly() throws Exception {
        when(subscriptionService.createCheckout(userId)).thenThrow(new ConflictException("Você já possui uma assinatura ativa."));

        mockMvc.perform(post("/api/v1/subscriptions/checkout").with(authentication(authToken)))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void checkoutShouldReturn503WhenStripeFails() throws Exception {
        when(subscriptionService.createCheckout(userId)).thenThrow(new PaymentProviderUnavailableException("down"));

        mockMvc.perform(post("/api/v1/subscriptions/checkout").with(authentication(authToken)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(503));
    }

    // ---- Story 2.4 ----

    @Test
    void cancelShouldReturn200WithCancelledStatusAndAccessUntil() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-09-01T12:00:00Z");
        Subscription sub = new Subscription(userId, Subscription.PlanType.MONTHLY, Subscription.Status.CANCELLED,
            start, start.plusDays(30));
        sub.setNextBillingDate(OffsetDateTime.parse("2026-11-30T12:00:00Z"));
        sub.setCancellationDate(OffsetDateTime.parse("2026-10-31T12:00:00Z"));
        when(subscriptionService.cancel(eq(userId), any())).thenReturn(sub);
        when(subscriptionService.hasAccess(sub)).thenReturn(true);

        mockMvc.perform(post("/api/v1/subscriptions/cancel").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.planType").value("MONTHLY"))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.nextBillingDate").value("2026-11-30T12:00:00Z"))
            .andExpect(jsonPath("$.cancellationDate").value("2026-10-31T12:00:00Z"))
            .andExpect(jsonPath("$.hasAccess").value(true));
    }

    @Test
    void cancelShouldReturn409WhenNotActiveMonthly() throws Exception {
        when(subscriptionService.cancel(eq(userId), any())).thenThrow(new ConflictException("Sua assinatura já está cancelada."));

        mockMvc.perform(post("/api/v1/subscriptions/cancel").with(authentication(authToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cancelShouldReturn503WhenStripeFails() throws Exception {
        when(subscriptionService.cancel(eq(userId), any())).thenThrow(new PaymentProviderUnavailableException("down"));

        mockMvc.perform(post("/api/v1/subscriptions/cancel").with(authentication(authToken)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void cancelShouldReturn404WhenNoSubscription() throws Exception {
        when(subscriptionService.cancel(eq(userId), any()))
            .thenThrow(new com.porquinho.exception.ResourceNotFoundException("Subscription not found"));

        mockMvc.perform(post("/api/v1/subscriptions/cancel").with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }
}
