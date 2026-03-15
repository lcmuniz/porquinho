package com.porquinho.repository;

import com.porquinho.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for AuditLog entity.
 * Provides CRUD operations for audit logging.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    // Additional query methods can be added here as needed
    // For example: findByUserIdOrderByTimestampDesc(UUID userId)
}
