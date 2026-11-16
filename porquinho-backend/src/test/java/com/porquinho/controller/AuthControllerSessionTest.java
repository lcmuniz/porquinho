package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.UserSession;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.service.AuthService;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.RateLimitService;
import com.porquinho.service.AccountLockService;
import com.porquinho.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AuthControllerSessionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private AccountLockService accountLockService;

    @MockBean
    private SessionService sessionService;

    private UUID userId;
    private UUID supabaseSessionId;
    private Jwt jwt;
    private JwtAuthenticationToken authToken;
    private static final String TEST_JWT = "test-jwt-token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        supabaseSessionId = UUID.randomUUID();
        jwt = Jwt.withTokenValue(TEST_JWT)
            .header("alg", "ES256")
            .subject(userId.toString())
            .claim("session_id", supabaseSessionId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        authToken = new JwtAuthenticationToken(jwt, List.of(), userId.toString());
    }

    @Test
    void getActiveSessionsMarksCurrentBySessionIdAfterRefresh() throws Exception {
        // Retro Epic 1 A2: the row was recorded with the login token; the request carries a refreshed one
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        UserSession current = new UserSession(userId, "login-token-hash", "Chrome", "1.1.1.1", OffsetDateTime.now().plusDays(7));
        current.setId(UUID.randomUUID());
        current.setSessionId(supabaseSessionId);
        UserSession other = new UserSession(userId, "other-hash", "Firefox", "2.2.2.2", OffsetDateTime.now().plusDays(7));
        other.setId(UUID.randomUUID());
        other.setSessionId(UUID.randomUUID());
        when(sessionService.getActiveSessions(userId)).thenReturn(List.of(current, other));

        mockMvc.perform(get("/api/v1/auth/sessions").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false));
    }

    @Test
    void getActiveSessionsShouldReturnSessionsList() throws Exception {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);

        UUID sessionId = UUID.randomUUID();
        UserSession session = new UserSession(userId, "abc123hash", "Chrome", "192.168.1.1",
            OffsetDateTime.now().plusDays(7));
        session.setId(sessionId);
        session.setCreatedAt(OffsetDateTime.now());
        session.setLastActivity(OffsetDateTime.now());

        when(sessionService.getActiveSessions(userId)).thenReturn(List.of(session));

        mockMvc.perform(get("/api/v1/auth/sessions")
                .with(authentication(authToken))
                .header("Authorization", "Bearer " + TEST_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$[0].deviceInfo").value("Chrome"))
                .andExpect(jsonPath("$[0].ipAddress").value("192.168.1.1"));

        verify(sessionService).getActiveSessions(userId);
    }

    @Test
    void revokeSessionShouldReturn204AndAuditLog() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sessionId)
                .with(authentication(authToken)))
                .andExpect(status().isNoContent());

        verify(sessionService).revokeSession(sessionId, userId);
        verify(auditLogService).log(eq("remote_logout"), eq(userId), anyString(), contains(sessionId.toString()));
    }

    @Test
    void revokeAllOtherSessionsShouldReturn204AndAuditLog() throws Exception {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(sessionService.revokeAllOtherSessions(userId, jwt)).thenReturn(3);

        mockMvc.perform(delete("/api/v1/auth/sessions")
                .with(authentication(authToken))
                .header("Authorization", "Bearer " + TEST_JWT))
                .andExpect(status().isNoContent());

        verify(sessionService).revokeAllOtherSessions(userId, jwt);
        verify(auditLogService).log(eq("remote_logout_all"), eq(userId), anyString(), contains("3"));
    }

    @Test
    void getSessionsShouldReturn429WhenRateLimited() throws Exception {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        mockMvc.perform(get("/api/v1/auth/sessions")
                .with(authentication(authToken)))
                .andExpect(status().isTooManyRequests());

        verify(sessionService, never()).getActiveSessions(any());
    }

    @Test
    void getSessionsShouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeSessionShouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/sessions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
