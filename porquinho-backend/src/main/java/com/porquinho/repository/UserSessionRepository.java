package com.porquinho.repository;

import com.porquinho.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId")
    List<UserSession> findAllByUserId(@Param("userId") UUID userId);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    Optional<UserSession> findByJwtTokenHash(String jwtTokenHash);

    /** Non-revoked row of a Supabase session (the @Where filter hides revoked ones). */
    Optional<UserSession> findFirstBySessionId(UUID sessionId);

    /**
     * Whether a Supabase session (JWT session_id claim) was revoked (retro Epic 1 A2) or expired after 7 days without
     * activity (NFR16, retro Epic 1 A3).
     * Native SQL: the @Where(revoked_at IS NULL) filter on UserSession would hide exactly the revoked rows.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM user_sessions WHERE session_id = :sessionId "
        + "AND (revoked_at IS NOT NULL OR expires_at < now()))", nativeQuery = true)
    boolean isSessionIdRevokedOrExpired(@Param("sessionId") UUID sessionId);

    /**
     * Records activity of a live session: last_activity = now and expires_at slides to now + 7 days (NFR16).
     * Keyed by session_id, which survives the token refresh. Throttled: rows touched after :touchedBefore are skipped,
     * so most requests write nothing.
     */
    @Transactional
    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivity = :now, s.expiresAt = :expiresAt "
        + "WHERE s.sessionId = :sessionId AND s.revokedAt IS NULL AND s.expiresAt > :now AND s.lastActivity < :touchedBefore")
    int updateLastActivity(@Param("sessionId") UUID sessionId, @Param("now") OffsetDateTime now,
                           @Param("expiresAt") OffsetDateTime expiresAt, @Param("touchedBefore") OffsetDateTime touchedBefore);

    /**
     * Hard-delete ALL sessions for a user, including revoked ones (Story 1.7 - account purge).
     *
     * CRITICAL: uses native SQL to bypass the @Where(revoked_at IS NULL) filter on UserSession.
     * A JPQL DELETE could apply that filter and silently leave revoked sessions in the table.
     * By the time this runs (during purge, 30 days after request), all sessions for the user
     * have revoked_at != NULL, so a JPQL DELETE would be a no-op.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_sessions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
