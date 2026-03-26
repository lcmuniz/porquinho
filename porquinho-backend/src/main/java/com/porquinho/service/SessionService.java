package com.porquinho.service;

import com.porquinho.entity.UserSession;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final JwtBlacklistService jwtBlacklistService;

    public SessionService(UserSessionRepository userSessionRepository, JwtBlacklistService jwtBlacklistService) {
        this.userSessionRepository = userSessionRepository;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Transactional
    public UserSession createSession(UUID userId, String jwtToken, String deviceInfo, String ipAddress) {
        String tokenHash = hashToken(jwtToken);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(7);

        UserSession session = new UserSession(userId, tokenHash, deviceInfo, ipAddress, expiresAt);
        return userSessionRepository.save(session);
    }

    @Transactional
    public List<UserSession> getActiveSessions(UUID userId) {
        cleanExpiredSessions(userId);
        return userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, OffsetDateTime.now());
    }

    @Transactional
    public void revokeSession(UUID sessionId, UUID userId) {
        UserSession session = userSessionRepository.findById(sessionId)
            .filter(s -> s.getUserId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        session.setRevokedAt(OffsetDateTime.now());
        userSessionRepository.save(session);

        jwtBlacklistService.blacklistToken(session.getJwtTokenHash(), session.getExpiresAt());
    }

    @Transactional
    public int revokeAllOtherSessions(UUID userId, String currentTokenHash) {
        UserSession currentSession = userSessionRepository.findByJwtTokenHash(currentTokenHash)
            .orElseThrow(() -> new ResourceNotFoundException("Current session not found"));

        // Get all other active sessions to blacklist their tokens
        List<UserSession> otherSessions = getActiveSessions(userId).stream()
            .filter(s -> !s.getId().equals(currentSession.getId()))
            .toList();

        for (UserSession session : otherSessions) {
            jwtBlacklistService.blacklistToken(session.getJwtTokenHash(), session.getExpiresAt());
        }

        return userSessionRepository.revokeAllByUserIdExceptSessionId(userId, currentSession.getId(), OffsetDateTime.now());
    }

    @Transactional
    public void updateLastActivity(String jwtToken) {
        String tokenHash = hashToken(jwtToken);
        userSessionRepository.updateLastActivity(tokenHash, OffsetDateTime.now());
    }

    @Transactional
    public int cleanExpiredSessions(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime inactivityThreshold = now.minusDays(7);
        List<UserSession> expired = userSessionRepository.findExpiredByInactivity(userId, now, inactivityThreshold);
        for (UserSession session : expired) {
            session.setRevokedAt(now);
            userSessionRepository.save(session);
        }
        return expired.size();
    }

    public static String hashToken(String jwtToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jwtToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
