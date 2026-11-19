package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.AdminHealthResponse.Redis;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.entity.SystemAlert;
import com.porquinho.repository.SystemAlertRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 10.5 through Flyway (V50), the real proxies and PostgreSQL: the partial unique index keeps one open episode,
 * the job e-mails the admins once, and resolve is idempotent through the API. Redis is forced down with a spy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
// Own context (spy + mock); closing it keeps the suite under max_connections
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemAlertIntegrationTest {

    @Autowired private SystemAlertService service;
    @Autowired private SystemAlertRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private PlatformTransactionManager txManager;

    @MockBean private EmailService emailService;
    @SpyBean private AdminService adminService;

    private UUID admin;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM system_alerts");
        admin = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin) VALUES (?, ?, 'EMAIL', true)", admin, admin + "@example.com");
        when(emailService.isConfigured()).thenReturn(true);
        when(emailService.sendSystemAlert(any(), any(), any())).thenReturn(true);
        doReturn(new Redis(false, null, null, null, Status.RED)).when(adminService).redis();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM system_alerts");
        jdbc.update("DELETE FROM users WHERE id = ?", admin);
    }

    @Test
    void partialUniqueIndexKeepsOneOpenEpisodePerKey() {
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        assertThat(insert("REDIS_DOWN", at)).isEqualTo(1);
        assertThat(insert("REDIS_DOWN", at.plusMinutes(5))).isZero();
        assertThat(insert("IMPORT_ERROR_RATE:ITAU", at)).isEqualTo(1);
        jdbc.update("UPDATE system_alerts SET normalized_at = ? WHERE alert_key = 'REDIS_DOWN'", at.plusMinutes(10));
        assertThat(insert("REDIS_DOWN", at.plusHours(2))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_alerts WHERE alert_key = 'REDIS_DOWN'", Long.class))
            .isEqualTo(2);
    }

    @Test
    void jobRecordsOnceAndEmailsTheAdminWithTheHealthLink() {
        service.runAlerts();
        service.runAlerts();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_alerts WHERE alert_key = 'REDIS_DOWN'", Long.class))
            .isEqualTo(1);
        ArgumentCaptor<SystemAlert> alert = ArgumentCaptor.forClass(SystemAlert.class);
        verify(emailService, times(1)).sendSystemAlert(eq(admin + "@example.com"), alert.capture(),
            eq("http://localhost:5173/admin?tab=health"));
        assertThat(alert.getValue().getAlertType()).isEqualTo("REDIS_DOWN");

        // Redis back: the episode ends, the row stays
        doReturn(new Redis(true, null, null, null, Status.GREEN)).when(adminService).redis();
        service.runAlerts();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_alerts WHERE alert_key = 'REDIS_DOWN' "
            + "AND normalized_at IS NOT NULL", Long.class)).isEqualTo(1);
    }

    @Test
    void resolveIsIdempotentAndTheOpenCountDrops() throws Exception {
        service.runAlerts();
        long id = repository.findFirstByAlertKeyOrderByTriggeredAtDesc("REDIS_DOWN").orElseThrow().getId();
        long open = repository.countByResolvedAtIsNull();

        mockMvc.perform(get("/api/v1/admin/alerts").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openCount").value(open))
            .andExpect(jsonPath("$.emailConfigured").value(true))
            .andExpect(jsonPath("$.alerts[?(@.key == 'REDIS_DOWN')].valueLabel").value("fora do ar"))
            .andExpect(jsonPath("$.alerts[?(@.key == 'REDIS_DOWN')].tab").value("health"));

        mockMvc.perform(post("/api/v1/admin/alerts/" + id + "/resolve").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        OffsetDateTime first = repository.findById(id).orElseThrow().getResolvedAt();
        mockMvc.perform(post("/api/v1/admin/alerts/" + id + "/resolve").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk());
        SystemAlert after = repository.findById(id).orElseThrow();
        assertThat(after.getResolvedAt()).isEqualTo(first);
        assertThat(after.getResolvedBy()).isEqualTo(admin);
        assertThat(repository.countByResolvedAtIsNull()).isEqualTo(open - 1);

        mockMvc.perform(post("/api/v1/admin/alerts/999999999/resolve").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void listShowsTheUnresolvedFirstSoTheyCanAlwaysBeResolved() {
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        // an old open alert behind 101 newer resolved ones
        jdbc.update("INSERT INTO system_alerts (alert_key, alert_type, metric_value, threshold, triggered_at) "
            + "VALUES ('REDIS_DOWN', 'REDIS_DOWN', 0, 1, ?)", at.minusDays(10));
        for (int i = 0; i < 101; i++) {
            jdbc.update("INSERT INTO system_alerts (alert_key, alert_type, metric_value, threshold, triggered_at, "
                + "normalized_at, resolved_at) VALUES ('UPTIME', 'UPTIME', 98, 99, ?, ?, ?)",
                at.minusMinutes(i), at.minusMinutes(i), at.minusMinutes(i));
        }

        var alerts = service.list().alerts();
        assertThat(alerts).hasSize(100);
        assertThat(alerts.get(0).key()).isEqualTo("REDIS_DOWN");
        assertThat(alerts.get(0).resolvedAt()).isNull();
        // then the most recent first
        assertThat(alerts.get(1).triggeredAt()).isAfter(alerts.get(2).triggeredAt());
    }

    private int insert(String key, OffsetDateTime at) {
        return new TransactionTemplate(txManager).execute(s ->
            repository.insertIfNoOpenEpisode(key, key.split(":")[0], null, BigDecimal.ZERO, BigDecimal.ONE, at));
    }
}
