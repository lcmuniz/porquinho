package com.porquinho.repository;

import com.porquinho.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId")
    List<UserSession> findAllByUserId(@Param("userId") UUID userId);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    Optional<UserSession> findByJwtTokenHash(String jwtTokenHash);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.userId = :userId AND s.id <> :sessionId AND s.revokedAt IS NULL")
    int revokeAllByUserIdExceptSessionId(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivity = :now WHERE s.jwtTokenHash = :jwtTokenHash AND s.revokedAt IS NULL")
    int updateLastActivity(@Param("jwtTokenHash") String jwtTokenHash, @Param("now") OffsetDateTime now);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now AND s.lastActivity < :inactivityThreshold")
    List<UserSession> findExpiredByInactivity(@Param("userId") UUID userId, @Param("now") OffsetDateTime now, @Param("inactivityThreshold") OffsetDateTime inactivityThreshold);

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
