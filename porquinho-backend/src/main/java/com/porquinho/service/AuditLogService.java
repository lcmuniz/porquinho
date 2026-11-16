package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.entity.AuditLog;
import com.porquinho.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for audit logging operations.
 * Handles creation of audit log entries for security and compliance (NFR20).
 */
@Service
public class AuditLogService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log an event for audit purposes.
     *
     * @param eventType Type of event (e.g., "user_registration", "user_login")
     * @param userId UUID of the user performing the action
     * @param ipAddress IP address of the client
     * @return Created AuditLog entity
     */
    @Transactional
    public AuditLog log(String eventType, UUID userId, String ipAddress) {
        return log(eventType, userId, ipAddress, null);
    }

    /**
     * Log an event with additional metadata.
     *
     * @param eventType Type of event
     * @param userId UUID of the user performing the action
     * @param ipAddress IP address of the client
     * @param metadata Additional event metadata (can be JSON string)
     * @return Created AuditLog entity
     */
    @Transactional
    public AuditLog log(String eventType, UUID userId, String ipAddress, String metadata) {
        AuditLog auditLog = new AuditLog(userId, eventType, ipAddress, metadata);
        return auditLogRepository.save(auditLog);
    }

    /**
     * Log an anonymous event (without user_id) in a NEW transaction.
     *
     * Uses REQUIRES_NEW propagation so the log is committed independently of the caller's
     * transaction. Critical for the LGPD account-purge flow (Story 1.7): the "account_purged"
     * audit row must survive even if the surrounding purge transaction rolls back, providing
     * a durable trail that the purge was attempted.
     *
     * Also used for security-sensitive operations like password reset requests where we don't
     * want to reveal whether an email exists in the database.
     *
     * @param eventType Type of event (e.g., "password_reset_requested", "account_purged")
     * @param ipAddress IP address of the client
     * @param metadata Additional event metadata (e.g., email, user_agent, deletion_id)
     * @return Created AuditLog entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAnonymous(String eventType, String ipAddress, String metadata) {
        AuditLog auditLog = new AuditLog(null, eventType, ipAddress, metadata);
        return auditLogRepository.save(auditLog);
    }

    /**
     * Metadata JSON from key/value pairs, escaped by Jackson (retro Epic 1 A7): User-Agent or e-mail cannot forge
     * fields. Numbers and booleans stay JSON numbers/booleans; anything else (UUID, YearMonth...) goes as its string.
     */
    public static String metadata(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("metadata expects key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            map.put(String.valueOf(keyValues[i]),
                value == null || value instanceof Number || value instanceof Boolean ? value : String.valueOf(value));
        }
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
