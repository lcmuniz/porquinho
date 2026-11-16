package com.porquinho.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.service.AuthService;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.RateLimitService;
import com.porquinho.service.AccountLockService;
import com.porquinho.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.porquinho.config.TestWebSecurityConfig;
import org.springframework.context.annotation.Import;

/**
 * Unit tests for AuthController.
 * Uses @WebMvcTest to test only the controller layer with mocked dependencies.
 * Security is disabled in test profile.
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void healthEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/auth/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Auth service is running"));
    }

    /** JWT as Supabase issues it: sub, e-mail and (Google) user_metadata. */
    private static org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth(
            UUID sub, String email) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
            .header("alg", "ES256")
            .subject(sub.toString())
            .claim("email", email)
            .claim("user_metadata", java.util.Map.of("sub", "google-sub", "provider_id", "google-sub"))
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(3600))
            .build();
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
            jwt, java.util.List.of(), sub.toString());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UUID sub, String email) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .authentication(jwtAuth(sub, email));
    }

    @Test
    void registerWithGoogleShouldReturnCreatedForNewUser() throws Exception {
        UUID sub = UUID.randomUUID();
        User newUser = new User("test@example.com", User.AuthProvider.GOOGLE, "google-sub");
        newUser.setId(sub);
        when(authService.registerOrGetUserFromGoogle(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new AuthService.UserRegistrationResult(newUser, true));

        mockMvc.perform(post("/api/v1/auth/register/google").with(csrf()).with(as(sub, "test@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.id").value(sub.toString()));

        // retro Epic 1 A3 (R1): Google sign-in is a login too
        verify(auditLogService).log(eq("user_login"), eq(sub), anyString());
    }

    @Test
    void registerWithGoogleShouldReturnOkForExistingUser() throws Exception {
        UUID sub = UUID.randomUUID();
        User existingUser = new User("existing@example.com", User.AuthProvider.GOOGLE, "google-sub");
        existingUser.setId(sub);
        when(authService.registerOrGetUserFromGoogle(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new AuthService.UserRegistrationResult(existingUser, false));

        mockMvc.perform(post("/api/v1/auth/register/google").with(csrf()).with(as(sub, "existing@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("existing@example.com"));

        verify(auditLogService).log(eq("user_login"), eq(sub), anyString());
    }

    @Test
    void registerWithoutJwtIs401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/google").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/register/email").with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(authService, org.mockito.Mockito.never())
            .registerOrGetUserFromGoogle(any(), any(), any(), any());
        verify(authService, org.mockito.Mockito.never()).registerOrGetUserFromEmail(any(), any(), any());
    }

    @Test
    void registerWithGoogleUsesJwtIdentityAndIgnoresBody() throws Exception {
        // retro Epic 1 A1 (S2): e-mail and googleId in the body are an attacker's choice; only the JWT counts
        UUID sub = UUID.randomUUID();
        User newUser = new User("me@example.com", User.AuthProvider.GOOGLE, "google-sub");
        newUser.setId(sub);
        when(authService.registerOrGetUserFromGoogle(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new AuthService.UserRegistrationResult(newUser, true));

        mockMvc.perform(post("/api/v1/auth/register/google")
                .with(csrf())
                .with(as(sub, "me@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"victim@example.com\",\"googleId\":\"victim-google\",\"name\":\"X\"}"))
                .andExpect(status().isCreated());

        verify(authService).registerOrGetUserFromGoogle(
            eq(sub.toString()), eq("me@example.com"), eq("google-sub"), anyString());
    }

    @Test
    void registerWithEmailUsesJwtIdentityAndIgnoresBody() throws Exception {
        UUID sub = UUID.randomUUID();
        User newUser = new User("me@example.com", User.AuthProvider.EMAIL, null);
        newUser.setId(sub);
        when(authService.registerOrGetUserFromEmail(anyString(), anyString(), anyString()))
            .thenReturn(new AuthService.UserRegistrationResult(newUser, true));

        mockMvc.perform(post("/api/v1/auth/register/email")
                .with(csrf())
                .with(as(sub, "me@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"victim@example.com\",\"name\":\"X\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authProvider").value("EMAIL"));

        verify(authService).registerOrGetUserFromEmail(eq(sub.toString()), eq("me@example.com"), anyString());
    }

    @Test
    void shouldReturnConflictWhenEmailBelongsToAnotherAccount() throws Exception {
        when(authService.registerOrGetUserFromEmail(anyString(), anyString(), anyString()))
            .thenThrow(new ConflictException("Este e-mail já está cadastrado em outra conta."));

        mockMvc.perform(post("/api/v1/auth/register/email").with(csrf()).with(as(UUID.randomUUID(), "x@example.com")))
                .andExpect(status().isConflict());
    }

    // ===== LOGIN TESTS (Story 1.3) =====

    @Test
    void shouldAllowLoginCheckWhenNotRateLimitedOrLocked() throws Exception {
        // Arrange
        String loginJson = "{\"email\":\"test@example.com\"}";
        when(accountLockService.isLocked(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login/check")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn429OnLoginCheckWhenRateLimited() throws Exception {
        // Arrange
        String loginJson = "{\"email\":\"test@example.com\"}";
        when(accountLockService.isLocked(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login/check")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldReturn423OnLoginCheckWhenAccountLocked() throws Exception {
        // Arrange
        String loginJson = "{\"email\":\"test@example.com\"}";
        when(accountLockService.isLocked(anyString(), anyString())).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login/check")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isLocked());
    }

    @Test
    void shouldRecordFailedLoginAttempt() throws Exception {
        // Arrange
        String loginJson = "{\"email\":\"test@example.com\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login/failed")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk());

        verify(accountLockService).recordFailedAttempt(eq("test@example.com"), anyString());
    }

    @Test
    void shouldLogSuccessfulLogin() throws Exception {
        UUID sub = UUID.randomUUID();
        User me = new User("me@example.com", User.AuthProvider.EMAIL, null);
        me.setId(sub);
        when(authService.findById(sub.toString())).thenReturn(java.util.Optional.of(me));

        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).with(as(sub, "me@example.com")))
                .andExpect(status().isOk());

        verify(auditLogService).log(eq("user_login"), eq(sub), anyString());
        verify(accountLockService).resetFailedAttempts(eq("me@example.com"), anyString());
    }

    @Test
    void loginUsesJwtSubAndIgnoresEmailInBody() throws Exception {
        // retro Epic 1 A1 (S1): with {email: victim} the old endpoint reset the victim's lock counter for the
        // attacker's IP and wrote session and audit rows on the victim's account
        UUID attacker = UUID.randomUUID();
        User me = new User("attacker@example.com", User.AuthProvider.EMAIL, null);
        me.setId(attacker);
        when(authService.findById(attacker.toString())).thenReturn(java.util.Optional.of(me));

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .with(as(attacker, "attacker@example.com"))
                .header("Authorization", "Bearer attacker-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"victim@example.com\"}"))
                .andExpect(status().isOk());

        verify(accountLockService).resetFailedAttempts(eq("attacker@example.com"), anyString());
        verify(accountLockService, org.mockito.Mockito.never()).resetFailedAttempts(eq("victim@example.com"), anyString());
        verify(auditLogService).log(eq("user_login"), eq(attacker), anyString());
        verify(sessionService).createSession(eq(attacker), eq("attacker-token"), any(), anyString());
    }

    @Test
    void loginWithoutJwtIs401AndTouchesNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"victim@example.com\"}"))
                .andExpect(status().isUnauthorized());

        verify(accountLockService, org.mockito.Mockito.never()).resetFailedAttempts(anyString(), anyString());
        verify(auditLogService, org.mockito.Mockito.never()).log(anyString(), any(UUID.class), anyString());
    }

    @Test
    void loginOfUserNotYetSyncedIs404() throws Exception {
        UUID sub = UUID.randomUUID();
        when(authService.findById(sub.toString())).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).with(as(sub, "new@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginCheckRateLimitIsPerNormalizedEmailAndIp() throws Exception {
        // retro Epic 1 A1 (S3): keyed by e-mail only, anyone could keep the victim at 429; and "Victim@" escaped it
        when(accountLockService.isLocked(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/login/check")
                .with(csrf())
                .with(r -> { r.setRemoteAddr("6.6.6.6"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Victim@Example.com\"}"))
                .andExpect(status().isOk());

        verify(rateLimitService).allowRequest("login:victim@example.com:6.6.6.6", 5, 60);
    }

    // ===== PASSWORD RESET TESTS (Story 1.4) =====

    @Test
    void shouldLogPasswordResetRequested() throws Exception {
        // Arrange
        String resetJson = "{\"email\":\"test@example.com\"}";
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/password-reset/requested")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetJson))
                .andExpect(status().isOk());

        // Verify rate limit was checked with correct parameters (3 requests per 3600 seconds)
        verify(rateLimitService).allowRequest("password_reset:test@example.com", 3, 3600);

        // Verify anonymous audit log was created (no user_id)
        verify(auditLogService).logAnonymous(anyString(), anyString(), anyString());
    }

    @Test
    void passwordResetRequestedShouldEscapeUserAgentInAuditMetadata() throws Exception {
        // retro Epic 1 A7 (S12): a crafted User-Agent cannot forge fields in the audit JSON
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/password-reset/requested")
                .with(csrf())
                .header("User-Agent", "x\",\"forged\":\"1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> metadata = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logAnonymous(eq("password_reset_requested"), anyString(), metadata.capture());
        var json = objectMapper.readTree(metadata.getValue());
        org.assertj.core.api.Assertions.assertThat(json.has("forged")).isFalse();
        org.assertj.core.api.Assertions.assertThat(json.get("user_agent").asText()).isEqualTo("x\",\"forged\":\"1");
        org.assertj.core.api.Assertions.assertThat(json.get("email").asText()).isEqualTo("test@example.com");
    }

    @Test
    void shouldLogPasswordResetCompleted() throws Exception {
        // Arrange
        // In test profile, authentication is disabled, so userId will be null
        // The endpoint should handle this gracefully

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/password-reset/completed")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Unauthenticated (test profile): no user id, so no audit entry
        verify(auditLogService, org.mockito.Mockito.never()).log(anyString(), any(UUID.class), anyString(), anyString());
    }

    @Test
    void passwordResetCompletedShouldAuditWithJwtSub() throws Exception {
        // Guards against regressing to @AuthenticationPrincipal String (null with JwtAuthenticationToken)
        UUID sub = UUID.randomUUID();
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
            .header("alg", "ES256")
            .subject(sub.toString())
            .issuedAt(java.time.Instant.now())
            .expiresAt(java.time.Instant.now().plusSeconds(3600))
            .build();
        var authToken = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
            jwt, java.util.List.of(), sub.toString());

        mockMvc.perform(post("/api/v1/auth/password-reset/completed")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(auditLogService).log(
            org.mockito.ArgumentMatchers.eq("password_reset_completed"),
            org.mockito.ArgumentMatchers.eq(sub),
            anyString(),
            anyString());
    }

    @Test
    void shouldReturn429WhenPasswordResetRateLimited() throws Exception {
        // Arrange
        String resetJson = "{\"email\":\"test@example.com\"}";
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/password-reset/requested")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetJson))
                .andExpect(status().isTooManyRequests());

        // Verify audit log was NOT created (rate limited before logging)
        verify(auditLogService, org.mockito.Mockito.never()).logAnonymous(anyString(), anyString(), anyString());
    }
}
