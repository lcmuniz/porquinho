package com.porquinho.service;

import com.porquinho.entity.UserSession;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.UserSessionRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final JwtBlacklistService jwtBlacklistService;
    private final SupabaseAdminClient supabaseAdminClient;

    public SessionService(UserSessionRepository userSessionRepository, JwtBlacklistService jwtBlacklistService,
                          SupabaseAdminClient supabaseAdminClient) {
        this.userSessionRepository = userSessionRepository;
        this.jwtBlacklistService = jwtBlacklistService;
        this.supabaseAdminClient = supabaseAdminClient;
    }

    @Transactional
    public UserSession createSession(UUID userId, Jwt jwt, String deviceInfo, String ipAddress) {
        // Reloading /auth/callback or repeating /login in the same Supabase session must not add a duplicate row
        UUID supabaseSessionId = sessionId(jwt);
        if (supabaseSessionId != null) {
            Optional<UserSession> existing = userSessionRepository.findFirstBySessionId(supabaseSessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        String tokenHash = hashToken(jwt.getTokenValue());
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(7);

        UserSession session = new UserSession(userId, tokenHash, deviceInfo, ipAddress, expiresAt);
        session.setSessionId(supabaseSessionId);
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

        // revoked_at on a row with session_id is what JwtAuthenticationConverter checks; the hash covers old rows
        session.setRevokedAt(OffsetDateTime.now());
        userSessionRepository.save(session);

        jwtBlacklistService.blacklistToken(session.getJwtTokenHash(), session.getExpiresAt());
    }

    /**
     * Revokes every session of the user except the request's one (same session_id, or same token for rows without
     * session_id), then signs the other sessions out in Supabase. 404 only when the request carries no session_id and
     * its token matches no session.
     */
    @Transactional
    public int revokeAllOtherSessions(UUID userId, Jwt currentJwt) {
        UUID currentSessionId = sessionId(currentJwt);
        String currentTokenHash = hashToken(currentJwt.getTokenValue());
        List<UserSession> sessions = userSessionRepository.findAllByUserId(userId);
        if (currentSessionId == null && sessions.stream().noneMatch(s -> s.isCurrent(null, currentTokenHash))) {
            throw new ResourceNotFoundException("Current session not found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<UserSession> otherSessions = sessions.stream()
            .filter(s -> !s.isCurrent(currentSessionId, currentTokenHash))
            .toList();
        for (UserSession session : otherSessions) {
            session.setRevokedAt(now);
            jwtBlacklistService.blacklistToken(session.getJwtTokenHash(), session.getExpiresAt());
        }
        userSessionRepository.saveAll(otherSessions);

        // HTTP call after commit, so a slow Supabase does not hold the transaction (up to 15s of timeouts)
        String accessToken = currentJwt.getTokenValue();
        AfterCommit.run(() -> supabaseAdminClient.signOut(accessToken, "others"));
        return otherSessions.size();
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

    /** The Supabase session_id claim (stable across token refreshes), or null when absent or not a UUID. */
    public static UUID sessionId(Jwt jwt) {
        String claim = jwt.getClaimAsString("session_id");
        if (claim == null) {
            return null;
        }
        try {
            return UUID.fromString(claim);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
