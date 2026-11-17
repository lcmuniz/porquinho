package com.porquinho.service;

import com.porquinho.dto.DataExportResponse;
import net.lingala.zip4j.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 9.3: the export job against the real schema (PostgreSQL via Testcontainers, Flyway). Not @Transactional:
 * the job only starts after the request commits, on another thread. Cleans up the rows and the file it creates.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataExportIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConfigurableApplicationContext context;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoSpyBean
    private EmailService emailService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (String path : jdbc.queryForList("SELECT file_path FROM data_exports WHERE user_id = ? AND file_path IS NOT NULL",
                String.class, userId)) {
            Files.deleteIfExists(Path.of(path));
        }
        jdbc.update("DELETE FROM data_exports WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void jobRunsAfterCommitOnAnotherThreadAndProducesTheZip() throws Exception {
        CompletableFuture<Thread> jobThread = new CompletableFuture<>();
        doAnswer(inv -> {
            jobThread.complete(Thread.currentThread());
            return null;
        }).when(emailService).sendDataExportReady(anyString(), anyString());

        DataExportResponse response = dataExportService.requestExport(userId, "10.0.0.1", PASSWORD);

        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(jobThread.get(15, TimeUnit.SECONDS)).isNotSameAs(Thread.currentThread());

        Map<String, Object> row = jdbc.queryForMap("SELECT status, file_path FROM data_exports WHERE id = ?", response.getId());
        assertThat(row).containsEntry("status", "READY");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'complete_data_export' AND ip_address = '10.0.0.1'",
            Integer.class, userId)).isEqualTo(1);

        try (ZipFile zip = new ZipFile(row.get("file_path").toString(), PASSWORD.toCharArray())) {
            assertThat(zip.getFileHeaders().stream().map(h -> h.getFileName()).toList()).containsExactly(
                "user_profile.json", "accounts.json", "transactions.json", "categories.json", "budgets.json",
                "goals.json", "audit_log.json", "export_metadata.json");
            String metadata = new String(zip.getInputStream(zip.getFileHeader("export_metadata.json")).readAllBytes());
            // schema_version comes from Flyway, not from a constant
            assertThat(metadata).containsPattern("\"schema_version\"\\s*:\\s*\"\\d+\"");
        }
    }

    // --- Stuck PROCESSING (pre-10.4, grupo A) ---

    private UUID insertProcessing(String age, String filePath) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO data_exports (id, user_id, status, file_path, download_token, created_at, expires_at) "
            + "VALUES (?, ?, 'PROCESSING', ?, ?, NOW() - CAST(? AS INTERVAL), NOW() + INTERVAL '1 day')",
            id, userId, filePath, UUID.randomUUID(), age);
        return id;
    }

    private Map<String, Object> exportRow(UUID id) {
        return jdbc.queryForMap("SELECT status, file_path FROM data_exports WHERE id = ?", id);
    }

    @Test
    void atStartupExportStuckBeforeTheRestartFailsDeletesTheFileAndFreesTheRateLimit() throws Exception {
        Path partial = Files.createTempFile("export_partial", ".zip");
        UUID stuck = insertProcessing("1 day", partial.toString());

        // the real trigger: the listener runs on ApplicationReadyEvent
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

        assertThat(exportRow(stuck)).containsEntry("status", "FAILED").containsEntry("file_path", null);
        assertThat(Files.exists(partial)).isFalse();
        verify(rateLimitService).reset("data_export:" + userId);
        // the polling shows the failure, so "Tentar de novo" is offered
        assertThat(dataExportService.getExportStatus(userId).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void hourlyCleanupFailsProcessingOlderThanOneHourAndKeepsASlowOne() {
        UUID slow = insertProcessing("40 minutes", null);
        UUID stuck = insertProcessing("2 hours", null);

        dataExportService.cleanupExpiredExports();

        assertThat(exportRow(slow)).containsEntry("status", "PROCESSING");
        assertThat(exportRow(stuck)).containsEntry("status", "FAILED");
        verify(rateLimitService).reset("data_export:" + userId);
    }

    @Test
    void startupSweepIgnoresExportsCreatedAfterTheBackendCameUp() {
        UUID running = insertProcessing("0 seconds", null);

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

        assertThat(exportRow(running)).containsEntry("status", "PROCESSING");
        verify(rateLimitService, never()).reset("data_export:" + userId);
    }
}
