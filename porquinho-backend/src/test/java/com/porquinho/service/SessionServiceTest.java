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
        UserSession result = sessionService.createSession(userId, jwtToken, deviceInfo, ipAddress);

        // Assert
        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(captor.capture());
        UserSession saved = captor.getValue();

        assertEquals(userId, saved.getUserId());
        assertEquals(tokenHash, saved.getJwtTokenHash());
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
    void revokeAllOtherSessionsShouldBlacklistOtherTokensAndCallRepository() {
        // Arrange
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();

        UserSession currentSession = new UserSession(userId, tokenHash, "Chrome", "1.2.3.4", OffsetDateTime.now().plusDays(7));
        currentSession.setId(currentSessionId);

        String otherTokenHash = SessionService.hashToken("other-token-value");
        UserSession otherSession = new UserSession(userId, otherTokenHash, "Firefox", "5.6.7.8", OffsetDateTime.now().plusDays(5));
        otherSession.setId(otherSessionId);

        when(userSessionRepository.findByJwtTokenHash(tokenHash)).thenReturn(Optional.of(currentSession));
        when(userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
            .thenReturn(List.of(currentSession, otherSession));
        when(userSessionRepository.revokeAllByUserIdExceptSessionId(eq(userId), eq(currentSessionId), any()))
            .thenReturn(1);

        // Act
        int result = sessionService.revokeAllOtherSessions(userId, tokenHash);

        // Assert
        assertEquals(1, result);
        verify(jwtBlacklistService).blacklistToken(eq(otherTokenHash), any(OffsetDateTime.class));
        verify(jwtBlacklistService, never()).blacklistToken(eq(tokenHash), any(OffsetDateTime.class));
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
    void updateLastActivityShouldCallRepositoryWithCorrectHash() {
        // Act
        sessionService.updateLastActivity(jwtToken);

        // Assert
        verify(userSessionRepository).updateLastActivity(eq(tokenHash), any(OffsetDateTime.class));
    }

    @Test
    void cleanExpiredSessionsShouldRevokeInactiveSessions() {
        // Arrange
        UserSession expiredSession = new UserSession(userId, "expired-hash", "Old Chrome", "1.1.1.1", OffsetDateTime.now().plusDays(1));
        expiredSession.setId(UUID.randomUUID());

        when(userSessionRepository.findExpiredByInactivity(eq(userId), any(), any()))
            .thenReturn(List.of(expiredSession));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        int result = sessionService.cleanExpiredSessions(userId);

        // Assert
        assertEquals(1, result);
        assertNotNull(expiredSession.getRevokedAt());
        verify(userSessionRepository).save(expiredSession);
    }
}
