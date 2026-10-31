package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.Subscription;
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
}
