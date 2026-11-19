package com.porquinho.repository;

import com.porquinho.service.HealthSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Story 10.1: the admin panel queries against the real database (Testcontainers + Flyway). */
@SpringBootTest
@ActiveProfiles("test")
// The @SpyBean gives this class its own context (and Hikari pool); closing it keeps the suite under max_connections
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemHealthSnapshotRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private HealthSnapshotService healthSnapshotService;
    @Autowired
    private UserSessionRepository userSessionRepository;
    @Autowired
    private UserRepository userRepository;
    @SpyBean
    private SystemHealthSnapshotRepository snapshotRepository;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM system_health_snapshots");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM system_health_snapshots");
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private void snapshot(String age, long requests, long errors, double totalMs) {
        jdbc.update("INSERT INTO system_health_snapshots (recorded_at, request_count, error_count, total_time_ms) "
            + "VALUES (now() - CAST(? AS interval), ?, ?, ?)", age, requests, errors, totalMs);
    }

    @Test
    void totalsSinceSumsOnlyTheRowsInsideTheWindow() {
        snapshot("1 minute", 10, 1, 100.5);
        snapshot("2 hours", 20, 2, 200.25);
        snapshot("25 hours", 1000, 500, 9999);

        HealthSnapshotService.Totals totals = healthSnapshotService.totalsSince(OffsetDateTime.now(ZoneOffset.UTC).minusHours(24));

        assertThat(totals).isEqualTo(new HealthSnapshotService.Totals(2, 30, 3, 300.75));
    }

    @Test
    void totalsSinceSumsTheOkRequests() {
        jdbc.update("INSERT INTO system_health_snapshots (recorded_at, request_count, error_count, total_time_ms, ok_requests) "
            + "VALUES (now() - interval '1 minute', 10, 0, 1, 4), (now() - interval '2 minutes', 5, 0, 1, 3)");
        assertThat(healthSnapshotService.totalsSince(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)).okRequests())
            .isEqualTo(7);
    }

    @Test
    void recordSnapshotDeletesOnlyRowsOlderThan31Days() {
        snapshot("32 days", 1, 0, 1);
        snapshot("30 days", 2, 0, 2);

        healthSnapshotService.recordSnapshot();

        List<Long> left = jdbc.queryForList("SELECT request_count FROM system_health_snapshots "
            + "WHERE recorded_at < now() - interval '1 day' ORDER BY recorded_at", Long.class);
        assertThat(left).containsExactly(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_health_snapshots", Long.class)).isEqualTo(2);
    }

    @Test
    void failedPurgeKeepsTheSavedSnapshot() {
        // The purge runs outside the save's transaction: its failure must not roll the new row back. The stub fails the
        // way a real one does inside a shared transaction (rollback-only); with no surrounding transaction it just throws.
        doAnswer(inv -> {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new RuntimeException("purge failed");
        }).when(snapshotRepository).deleteRecordedBefore(any());

        healthSnapshotService.recordSnapshot();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_health_snapshots", Long.class)).isEqualTo(1);
    }

    @Test
    void countActiveLeavesRevokedExpiredAndIdleSessionsOut() {
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        String insert = "INSERT INTO user_sessions (user_id, jwt_token_hash, last_activity, expires_at, revoked_at) "
            + "VALUES (?, ?, now() - CAST(? AS interval), now() + CAST(? AS interval), %s)";
        jdbc.update(insert.formatted("now()"), userId, "revoked", "1 minute", "1 day");
        jdbc.update(insert.formatted("NULL"), userId, "expired", "1 minute", "-1 minute");
        jdbc.update(insert.formatted("NULL"), userId, "idle", "31 minutes", "1 day");
        jdbc.update(insert.formatted("NULL"), userId, "active", "1 minute", "1 day");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long others = jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE user_id <> ? AND revoked_at IS NULL "
            + "AND expires_at > now() AND last_activity >= now() - interval '30 minutes'", Long.class, userId);

        assertThat(userSessionRepository.countActive(now, now.minusMinutes(30))).isEqualTo(others + 1);
    }

    @Test
    void softDeletedAdminIsNotAdmin() {
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin, deleted_at) VALUES (?, ?, 'EMAIL', true, now())",
            userId, userId + "@example.com");

        assertThat(userRepository.existsByIdAndAdminTrue(userId)).isFalse();
    }

    @Test
    void adminEmailsLeaveSoftDeletedAdminsOut() {
        UUID deleted = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin, deleted_at) VALUES (?, ?, 'EMAIL', true, now())",
            deleted, deleted + "@example.com");
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin) VALUES (?, ?, 'EMAIL', true)",
            userId, userId + "@example.com");
        try {
            assertThat(userRepository.findAdminEmails()).contains(userId + "@example.com")
                .doesNotContain(deleted + "@example.com");
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", deleted);
        }
    }
}
