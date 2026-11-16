package com.porquinho.repository;

import com.porquinho.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retro Epic 1 S8: the purge's delete of anonymous audits by e-mail against the real PostgreSQL (Testcontainers).
 * Rows are written in both formats the code ever used: Jackson (AuditLogService.metadata) and the old String.format.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogRepositoryIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private void insertAudit(UUID userId, String eventType, String metadata) {
        jdbc.update("INSERT INTO audit_logs (user_id, event_type, metadata) VALUES (?, ?, ?)", userId, eventType, metadata);
    }

    private List<String> remainingMetadata() {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE event_type IN ('password_reset_requested', 'user_login')",
            String.class);
    }

    @Test
    void deletesOnlyAnonymousRowsWithThatEmail() {
        UUID owner = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", owner, owner + "@example.com");
        jdbc.update("DELETE FROM audit_logs WHERE event_type IN ('password_reset_requested', 'user_login')");

        // Jackson format, typed with another case
        insertAudit(null, "password_reset_requested", AuditLogService.metadata("email", "Ana_Silva@Example.com", "user_agent", "x"));
        // Old String.format format
        insertAudit(null, "password_reset_requested", "{\"email\":\"ana_silva@example.com\",\"user_agent\":\"unknown\"}");
        // "_" is a LIKE wildcard: this other titular must survive
        String other = AuditLogService.metadata("email", "anaxsilva@example.com", "user_agent", "x");
        insertAudit(null, "password_reset_requested", other);
        // Same e-mail as a prefix of a longer address must survive
        String longer = AuditLogService.metadata("email", "ana_silva@example.com.br", "user_agent", "x");
        insertAudit(null, "password_reset_requested", longer);
        // A row bound to a user is deleted by deleteByUserId, not here
        String bound = AuditLogService.metadata("email", "ana_silva@example.com");
        insertAudit(owner, "user_login", bound);

        int deleted = auditLogRepository.deleteAnonymousByMetadataEntry("\"email\":\"ana_silva@example.com\"");

        assertThat(deleted).isEqualTo(2);
        assertThat(remainingMetadata()).containsExactlyInAnyOrder(other, longer, bound);
    }
}
