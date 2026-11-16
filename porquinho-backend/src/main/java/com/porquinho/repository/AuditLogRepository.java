package com.porquinho.repository;

import com.porquinho.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for AuditLog entity.
 * Provides CRUD operations for audit logging.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId);

    /**
     * Hard-delete all audit logs for a specific user (Story 1.7 - account purge).
     *
     * CRITICAL: filter is exactly "userId = :userId". This intentionally PRESERVES
     * anonymous logs (user_id IS NULL) such as the "account_purged" event written
     * before this delete runs - which is required by AC8 to maintain LGPD audit trail.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AuditLog a WHERE a.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * Retro Epic 1 A4 (S8): hard-delete anonymous logs (user_id IS NULL) whose metadata text contains the given
     * JSON entry, compared case-insensitively. Used by the purge to drop "password_reset_requested" rows that keep
     * the titular's e-mail. strpos instead of LIKE: "_" and "%" are valid e-mail characters.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM audit_logs WHERE user_id IS NULL AND strpos(lower(metadata), lower(:entry)) > 0",
           nativeQuery = true)
    int deleteAnonymousByMetadataEntry(@Param("entry") String entry);
}
