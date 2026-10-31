package com.porquinho.config;

import com.porquinho.controller.SubscriptionController;
import com.porquinho.service.JwtBlacklistService;
import com.porquinho.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Story 2.1 (AC4): exercises SubscriptionAccessFilter through the REAL production
 * SecurityConfig chain (bearer token → JwtAuthenticationConverter → filter), which the
 * unit test cannot see. Uses a non-"test" profile because SecurityConfig is @Profile("!test").
 * Breaks if the filter is unregistered or moved before BearerTokenAuthenticationFilter.
 */
@WebMvcTest(SubscriptionController.class)
@ActiveProfiles("security-it")
@Import({SecurityConfig.class, SubscriptionAccessFilterIntegrationTest.JwtDecoderConfig.class})
class SubscriptionAccessFilterIntegrationTest {

    static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TestConfiguration
    static class JwtDecoderConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(USER_ID.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtBlacklistService jwtBlacklistService;

    @Test
    void protectedRouteWithoutAccessReturns402ProblemDetail() throws Exception {
        when(subscriptionService.hasAccess(USER_ID)).thenReturn(false);

        mockMvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer any-token"))
            .andExpect(status().isPaymentRequired())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(402))
            .andExpect(jsonPath("$.title").value("Assinatura necessária"));
    }

    @Test
    void protectedRouteWithAccessPassesThroughFilter() throws Exception {
        when(subscriptionService.hasAccess(USER_ID)).thenReturn(true);

        // No controller for this path: reaching MVC (404) proves the filter let it through
        mockMvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer any-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void exemptRouteIsNotBlockedEvenWithoutAccess() throws Exception {
        when(subscriptionService.hasAccess(USER_ID)).thenReturn(false);
        when(subscriptionService.findByUserId(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/subscriptions/me").header("Authorization", "Bearer any-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejectedBeforeSubscriptionCheck() throws Exception {
        mockMvc.perform(get("/api/v1/budgets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void stripeWebhookIsPublicWithoutJwt() throws Exception {
        // Story 2.2: permitAll in SecurityConfig. The webhook controller is not in this slice,
        // so 404 (reached MVC) proves the request was neither 401 nor 402.
        mockMvc.perform(post("/api/v1/webhooks/stripe").content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void expiredTrialUserCanStillStartCheckout() throws Exception {
        // Story 2.2: /api/v1/subscriptions/ is exempt from the 402 filter, so the user can pay
        when(subscriptionService.hasAccess(USER_ID)).thenReturn(false);
        when(subscriptionService.createCheckout(USER_ID)).thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");

        mockMvc.perform(post("/api/v1/subscriptions/checkout").header("Authorization", "Bearer any-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/cs_test_1"));
    }
}
