package com.porquinho.config;

import com.porquinho.repository.UserRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Retro Epic 1 A2 (S6). */
class DeletedAccountFilterTest {

    private UserRepository userRepository;
    private DeletedAccountFilter filter;
    private FilterChain chain;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        filter = new DeletedAccountFilter(userRepository);
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
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/transactions", "/api/v1/auth/sessions", "/api/v1/data-export",
        "/api/v1/subscriptions/me"})
    void accountInDeletionGets403ProblemDetail(String path) throws Exception {
        authenticateAs(userId.toString());
        when(userRepository.isDeletionPending(userId)).thenReturn(true);

        MockHttpServletResponse response = run(path);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"title\":\"Conta em exclusão\"");
        assertThat(body).contains("\"detail\":\"Esta conta está em exclusão e não pode mais ser usada.\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void activeAccountPasses() throws Exception {
        authenticateAs(userId.toString());
        when(userRepository.isDeletionPending(userId)).thenReturn(false);

        assertThat(run("/api/v1/transactions").getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/account-deletion", "/actuator/health"})
    void exemptRoutesSkipTheLookup(String path) throws Exception {
        authenticateAs(userId.toString());

        assertThat(run(path).getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        verify(userRepository, never()).isDeletionPending(any());
    }

    @Test
    void anonymousRequestSkipsTheLookup() throws Exception {
        assertThat(run("/api/v1/transactions").getStatus()).isEqualTo(200);
        verify(userRepository, never()).isDeletionPending(any());
    }
}
