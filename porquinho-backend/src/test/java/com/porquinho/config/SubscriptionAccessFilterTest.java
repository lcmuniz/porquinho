package com.porquinho.config;

import com.porquinho.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionAccessFilterTest {

    private SubscriptionService subscriptionService;
    private SubscriptionAccessFilter filter;
    private FilterChain chain;
    private UUID userId;

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);
        filter = new SubscriptionAccessFilter(subscriptionService);
        chain = mock(FilterChain.class);
        userId = UUID.randomUUID();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "ES256")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), subject));
    }

    private MockHttpServletResponse run(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void shouldReturn402ProblemDetailOnProtectedRouteWithoutAccess() throws Exception {
        authenticateAs(userId.toString());
        when(subscriptionService.hasAccess(userId)).thenReturn(false);

        MockHttpServletResponse response = run("/api/v1/transactions");

        assertThat(response.getStatus()).isEqualTo(402);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"status\":402");
        assertThat(body).contains("\"title\":\"Assinatura necessária\"");
        assertThat(body).contains("\"detail\":\"Seu período de trial terminou. Assine para continuar.\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldPassOnProtectedRouteWithAccess() throws Exception {
        authenticateAs(userId.toString());
        when(subscriptionService.hasAccess(userId)).thenReturn(true);

        MockHttpServletResponse response = run("/api/v1/transactions");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/auth/sessions",
        "/api/v1/auth/register/google",
        "/api/v1/subscriptions/me",
        "/api/v1/data-export",
        "/api/v1/data-export/status",
        "/api/v1/account-deletion",
        "/actuator/health"
    })
    void shouldAlwaysPassOnExemptRoutes(String path) throws Exception {
        authenticateAs(userId.toString());

        MockHttpServletResponse response = run(path);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        verify(subscriptionService, never()).hasAccess(any(UUID.class));
    }

    @Test
    void shouldPassWhenNotAuthenticated() throws Exception {
        MockHttpServletResponse response = run("/api/v1/transactions");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        verify(subscriptionService, never()).hasAccess(any(UUID.class));
    }

    @Test
    void shouldPassWhenSubjectIsNotUuid() throws Exception {
        authenticateAs("not-a-uuid");

        MockHttpServletResponse response = run("/api/v1/transactions");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        verify(subscriptionService, never()).hasAccess(any(UUID.class));
    }
}
