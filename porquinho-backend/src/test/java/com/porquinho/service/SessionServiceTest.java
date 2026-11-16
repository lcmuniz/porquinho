package com.porquinho.service;

import com.porquinho.entity.UserSession;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @Mock
    private SupabaseAdminClient supabaseAdminClient;

    @InjectMocks
    private SessionService sessionService;

    private UUID userId;
    private String jwtToken;
    private String tokenHash;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
        tokenHash = SessionService.hashToken(jwtToken);
    }

    private static Jwt jwt(String tokenValue, UUID sessionId) {
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue).header("alg", "ES256").subject("sub")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
        if (sessionId != null) {
            builder.claim("session_id", sessionId.toString());
        }
        return builder.build();
    }

    private UserSession session(String hash, UUID supabaseSessionId) {
        UserSession s = new UserSession(userId, hash, "Chrome", "1.2.3.4", OffsetDateTime.now().plusDays(7));
        s.setId(UUID.randomUUID());
        s.setSessionId(supabaseSessionId);
        return s;
    }

    @Test
    void createSessionShouldSaveSessionWithCorrectFields() {
        // Arrange
        String deviceInfo = "Mozilla/5.0 Chrome/120.0";
        String ipAddress = "192.168.1.1";

        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // Act
        UUID supabaseSessionId = UUID.randomUUID();
        UserSession result = sessionService.createSession(userId, jwt(jwtToken, supabaseSessionId), deviceInfo, ipAddress);

        // Assert
        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(captor.capture());
        UserSession saved = captor.getValue();

        assertEquals(userId, saved.getUserId());
        assertEquals(tokenHash, saved.getJwtTokenHash());
        assertEquals(supabaseSessionId, saved.getSessionId());
        assertEquals(deviceInfo, saved.getDeviceInfo());
        assertEquals(ipAddress, saved.getIpAddress());
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    void getActiveSessionsShouldReturnNonRevokedNonExpiredSessions() {
        // Arrange
        UserSession session = new UserSession(userId, tokenHash, "Chrome", "1.2.3.4", OffsetDateTime.now().plusDays(7));
        session.setId(UUID.randomUUID());

        when(userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
            .thenReturn(List.of(session));

        // Act
        List<UserSession> result = sessionService.getActiveSessions(userId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(tokenHash, result.get(0).getJwtTokenHash());
    }

    @Test
    void revokeSessionShouldSetRevokedAtAndBlacklistToken() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UserSession session = new UserSession(userId, tokenHash, "Chrome", "1.2.3.4", OffsetDateTime.now().plusDays(7));
        session.setId(sessionId);
        session.setUserId(userId);

        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        sessionService.revokeSession(sessionId, userId);

        // Assert
        assertNotNull(session.getRevokedAt());
        verify(jwtBlacklistService).blacklistToken(eq(tokenHash), any(OffsetDateTime.class));
        verify(userSessionRepository).save(session);
    }

    @Test
    void revokeSessionShouldThrowWhenSessionNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            sessionService.revokeSession(sessionId, userId));
    }

    @Test
    void revokeSessionShouldThrowWhenSessionBelongsToDifferentUser() {
        UUID sessionId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserSession session = new UserSession(otherUserId, tokenHash, "Chrome", "1.2.3.4", OffsetDateTime.now().plusDays(7));
        session.setId(sessionId);
        session.setUserId(otherUserId);

        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(ResourceNotFoundException.class, () ->
            sessionService.revokeSession(sessionId, userId));
    }

    @Test
    void createSessionReusesLiveRowOfSameSupabaseSession() {
        UUID supabaseSessionId = UUID.randomUUID();
        UserSession existing = session("login-hash", supabaseSessionId);
        when(userSessionRepository.findFirstBySessionId(supabaseSessionId)).thenReturn(Optional.of(existing));

        UserSession result = sessionService.createSession(userId, jwt("refreshed-token", supabaseSessionId), "Chrome", "1.1.1.1");

        assertSame(existing, result);
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void createSessionWithoutSessionIdClaimKeepsItNull() {
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSession result = sessionService.createSession(userId, jwt(jwtToken, null), "Chrome", "1.1.1.1");

        assertNull(result.getSessionId());
        assertEquals(tokenHash, result.getJwtTokenHash());
    }

    @Test
    void revokeAllOtherSessionsFindsCurrentBySessionIdAfterRefresh() {
        // Retro Epic 1 A2: the current row holds the login token's hash; the request carries a refreshed token
        UUID currentSid = UUID.randomUUID();
        UserSession current = session(SessionService.hashToken("login-token"), currentSid);
        UserSession other = session(SessionService.hashToken("other-token"), UUID.randomUUID());
        UserSession legacy = session(SessionService.hashToken("legacy-token"), null);
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of(current, other, legacy));

        Jwt refreshed = jwt("refreshed-token", currentSid);
        int result = sessionService.revokeAllOtherSessions(userId, refreshed);

        assertEquals(2, result);
        assertNull(current.getRevokedAt());
        assertNotNull(other.getRevokedAt());
        assertNotNull(legacy.getRevokedAt());
        verify(jwtBlacklistService).blacklistToken(eq(legacy.getJwtTokenHash()), any(OffsetDateTime.class));
        verify(jwtBlacklistService, never()).blacklistToken(eq(current.getJwtTokenHash()), any(OffsetDateTime.class));
        verify(userSessionRepository).saveAll(List.of(other, legacy));
        verify(supabaseAdminClient).signOut("refreshed-token", "others");
    }

    @Test
    void revokeAllOtherSessionsKeepsLegacyRowOfCurrentToken() {
        // Row recorded before V41 (no session_id) for the very token of the request: it is the current one
        UserSession currentLegacy = session(tokenHash, null);
        UserSession other = session(SessionService.hashToken("other-token"), UUID.randomUUID());
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of(currentLegacy, other));

        int result = sessionService.revokeAllOtherSessions(userId, jwt(jwtToken, UUID.randomUUID()));

        assertEquals(1, result);
        assertNull(currentLegacy.getRevokedAt());
        assertNotNull(other.getRevokedAt());
    }

    @Test
    void revokeAllOtherSessionsWithoutSessionIdFallsBackToHash() {
        UserSession current = session(tokenHash, null);
        UserSession other = session(SessionService.hashToken("other-token"), null);
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of(current, other));

        int result = sessionService.revokeAllOtherSessions(userId, jwt(jwtToken, null));

        assertEquals(1, result);
        assertNull(current.getRevokedAt());
        verify(jwtBlacklistService).blacklistToken(eq(other.getJwtTokenHash()), any(OffsetDateTime.class));
        verify(jwtBlacklistService, never()).blacklistToken(eq(tokenHash), any(OffsetDateTime.class));
    }

    @Test
    void revokeAllOtherSessionsWithoutSessionIdAndUnknownTokenIs404() {
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of(session("other-hash", null)));

        assertThrows(ResourceNotFoundException.class, () ->
            sessionService.revokeAllOtherSessions(userId, jwt(jwtToken, null)));
        verify(supabaseAdminClient, never()).signOut(any(), any());
    }

    @Test
    void sessionIdIgnoresMissingOrInvalidClaim() {
        UUID sid = UUID.randomUUID();
        assertEquals(sid, SessionService.sessionId(jwt("t", sid)));
        assertNull(SessionService.sessionId(jwt("t", null)));
        Jwt invalid = Jwt.withTokenValue("t").header("alg", "ES256").claim("session_id", "not-a-uuid").build();
        assertNull(SessionService.sessionId(invalid));
    }

    @Test
    void hashTokenShouldReturnConsistentSha256Hash() {
        String hash1 = SessionService.hashToken("test-token");
        String hash2 = SessionService.hashToken("test-token");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 produces 64 hex chars
    }

    @Test
    void hashTokenShouldProduceDifferentHashesForDifferentTokens() {
        String hash1 = SessionService.hashToken("token-1");
        String hash2 = SessionService.hashToken("token-2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void updateLastActivitySlidesExpiryBySessionIdWithThrottle() {
        UUID sid = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();

        sessionService.updateLastActivity(sid);

        ArgumentCaptor<OffsetDateTime> now = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> touchedBefore = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(userSessionRepository).updateLastActivity(eq(sid), now.capture(), expiresAt.capture(), touchedBefore.capture());
        assertFalse(now.getValue().isBefore(before));
        assertEquals(now.getValue().plusDays(7), expiresAt.getValue());
        assertEquals(now.getValue().minusMinutes(5), touchedBefore.getValue());
    }
}
