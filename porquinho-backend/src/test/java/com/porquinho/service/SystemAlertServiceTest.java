package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse.Redis;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.entity.SystemAlert;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.SystemAlertRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import com.porquinho.service.SystemAlertService.Metric;
import com.porquinho.service.SystemAlertService.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Story 10.5: I/O matrix and the border of every metric. Clock fixed at 2026-11-19 13:20 UTC (10:20 in Sao Paulo).
 * Baseline: every metric healthy; each test breaks one.
 */
@ExtendWith(MockitoExtension.class)
class SystemAlertServiceTest {

    static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 19, 13, 20, 0, 0, ZoneOffset.UTC);

    @Mock private SystemAlertRepository repository;
    @Mock private HealthSnapshotService snapshots;
    @Mock private AdminService adminService;
    @Mock private UserSessionRepository sessions;
    @Mock private TransactionImportRepository imports;
    @Mock private AiClassificationHistoryRepository ai;
    @Mock private UserRepository users;
    @Mock private EmailService email;
    @Mock private SlackNotifier slack;

    private SystemAlertService service;

    @BeforeEach
    void setUp() {
        service = new SystemAlertService(repository, snapshots, adminService, sessions, imports, ai, users, email, slack,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), "https://app.example.com/", null);
        ReflectionTestUtils.setField(service, "self", service);
        lenient().when(snapshots.totalsSince(any())).thenReturn(new HealthSnapshotService.Totals(60, 1000, 0, 100_000));
        lenient().when(snapshots.uptimePercent(Duration.ofDays(30))).thenReturn(100.0);
        lenient().when(sessions.countActive(any(), any())).thenReturn(3L);
        lenient().when(adminService.redis()).thenReturn(new Redis(true, null, null, null, Status.GREEN));
        lenient().when(adminService.pool()).thenReturn(new AdminService.Pool(1, 9, 10, 0, Status.GREEN));
        lenient().when(repository.findFirstByAlertKeyOrderByTriggeredAtDesc(any())).thenReturn(Optional.empty());
        lenient().when(repository.insertIfNoOpenEpisode(any(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(users.findAdminEmails()).thenReturn(List.of("a@example.com", "b@example.com"));
        lenient().when(email.isConfigured()).thenReturn(true);
        lenient().when(email.sendSystemAlert(any(), any(), any())).thenReturn(true);
        lenient().when(slack.isConfigured()).thenReturn(true);
        lenient().when(slack.send(any())).thenReturn(true);
    }

    private Map<String, Violation> collect() {
        return service.collect(NOW, EnumSet.noneOf(Metric.class));
    }

    private void redisDown() {
        when(adminService.redis()).thenReturn(new Redis(false, null, null, null, Status.RED));
    }

    private static SystemAlert alert(String key, OffsetDateTime triggered, OffsetDateTime normalized) {
        SystemAlert a = new SystemAlert(key, key.split(":")[0], null, BigDecimal.ZERO, BigDecimal.ONE, triggered);
        ReflectionTestUtils.setField(a, "normalizedAt", normalized);
        return a;
    }

    // --- I/O matrix

    @Test
    void newViolationRecordsAndSendsEmailToEveryAdminAndSlack() {
        redisDown();
        service.runAlerts();

        verify(repository).insertIfNoOpenEpisode("REDIS_DOWN", "REDIS_DOWN", null, BigDecimal.ZERO, BigDecimal.ONE, NOW);
        ArgumentCaptor<SystemAlert> sent = ArgumentCaptor.forClass(SystemAlert.class);
        verify(email).sendSystemAlert(eq("a@example.com"), sent.capture(), eq("https://app.example.com/admin?tab=health"));
        verify(email).sendSystemAlert(eq("b@example.com"), any(), any());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(slack).send(text.capture());
        assertThat(text.getValue()).contains("Métrica: Redis", "Valor atual: fora do ar", "Limite: no ar",
            "Hora da violação: 19/11/2026 10:20 (horário de Brasília)", "Ação sugerida: Verifique o container do Redis",
            "https://app.example.com/admin?tab=health");
        assertThat(sent.getValue().getAlertKey()).isEqualTo("REDIS_DOWN");
    }

    @Test
    void stillViolatingWithOpenEpisodeDoesNothing() {
        redisDown();
        when(repository.findFirstByAlertKeyOrderByTriggeredAtDesc("REDIS_DOWN"))
            .thenReturn(Optional.of(alert("REDIS_DOWN", NOW.minusMinutes(15), null)));
        service.runAlerts();
        verify(repository, never()).insertIfNoOpenEpisode(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(email, slack);
    }

    @Test
    void resolvedButStillViolatingStaysQuiet() {
        redisDown();
        SystemAlert resolved = alert("REDIS_DOWN", NOW.minusHours(3), null);
        ReflectionTestUtils.setField(resolved, "resolvedAt", NOW.minusHours(2));
        when(repository.findFirstByAlertKeyOrderByTriggeredAtDesc("REDIS_DOWN")).thenReturn(Optional.of(resolved));
        when(repository.findByNormalizedAtIsNull()).thenReturn(List.of(resolved));
        service.runAlerts();
        verify(repository, never()).insertIfNoOpenEpisode(any(), any(), any(), any(), any(), any());
        verify(repository, never()).normalize(any(), any());
        verifyNoInteractions(email, slack);
    }

    @Test
    void normalizedAndBackWaitsOneHourFromTheLastAlert() {
        // last 09:50, normalized 10:05 (Sao Paulo), violating again at 10:20: nothing until 10:50
        SystemAlert last = alert("REDIS_DOWN", NOW.minusMinutes(30), NOW.minusMinutes(15));
        when(repository.findFirstByAlertKeyOrderByTriggeredAtDesc("REDIS_DOWN")).thenReturn(Optional.of(last));
        Violation v = new Violation(Metric.REDIS_DOWN, null, BigDecimal.ZERO, BigDecimal.ONE);

        assertThat(service.open(v, NOW)).isNull();
        assertThat(service.open(v, NOW.plusMinutes(29).plusSeconds(59))).isNull();
        // a few ms past 10:50 still counts as the hour
        assertThat(service.open(v, NOW.plusMinutes(30).plusNanos(5_000_000))).isNotNull();
        verify(repository, times(1)).insertIfNoOpenEpisode(any(), any(), any(), any(), any(), any());
    }

    @Test
    void perBankOnlyTheViolatingBankFires() {
        when(imports.countByBank(NOW.minusHours(24), NOW, null)).thenReturn(List.of(
            importCounts("ITAU", 88, 12), importCounts("NUBANK", 97, 3)));
        assertThat(collect()).containsOnlyKeys("IMPORT_ERROR_RATE:ITAU");
        assertThat(collect().get("IMPORT_ERROR_RATE:ITAU").value()).isEqualByComparingTo("12.0");
    }

    @Test
    void withoutDataNothingFiresAndOpenEpisodesNormalize() {
        when(snapshots.totalsSince(any())).thenReturn(new HealthSnapshotService.Totals(0, 0, 0, 0));
        when(snapshots.uptimePercent(Duration.ofDays(30))).thenReturn(null);
        when(sessions.countActive(any(), any())).thenReturn(0L);
        when(imports.countByBank(any(), any(), any())).thenReturn(List.of(importCounts("ITAU", 0, 9)));
        when(ai.countByBank(any(), any(), any())).thenReturn(List.of(aiCounts("ITAU", 19, 19)));
        assertThat(collect()).isEmpty();

        when(repository.findByNormalizedAtIsNull()).thenReturn(List.of(alert("IMPORT_ERROR_RATE:ITAU", NOW.minusHours(2), null)));
        service.runAlerts();
        verify(repository).normalize("IMPORT_ERROR_RATE:ITAU", NOW);
        verifyNoInteractions(email, slack);
    }

    @Test
    void emailNotConfiguredStillRecordsTheAlert() {
        redisDown();
        when(email.isConfigured()).thenReturn(false);
        service.runAlerts();
        verify(repository).insertIfNoOpenEpisode(eq("REDIS_DOWN"), any(), any(), any(), any(), any());
        verify(email, never()).sendSystemAlert(any(), any(), any());
        verify(slack).send(any());
    }

    @Test
    void slackDownStillSendsTheEmail() {
        redisDown();
        when(slack.send(any())).thenReturn(false);
        service.runAlerts();
        verify(email, times(2)).sendSystemAlert(any(), any(), any());
    }

    @Test
    void slackNotConfiguredIsNotCalled() {
        redisDown();
        when(slack.isConfigured()).thenReturn(false);
        service.runAlerts();
        verify(slack, never()).send(any());
        verify(email, times(2)).sendSystemAlert(any(), any(), any());
    }

    @Test
    void insertLosingTheRaceSendsNothing() {
        redisDown();
        when(repository.insertIfNoOpenEpisode(any(), any(), any(), any(), any(), any())).thenReturn(0);
        service.runAlerts();
        verifyNoInteractions(email, slack);
    }

    @Test
    void aFailedCheckDoesNotNormalizeItsEpisode() {
        when(adminService.pool()).thenThrow(new IllegalStateException("boom"));
        when(repository.findByNormalizedAtIsNull()).thenReturn(List.of(alert("DB_POOL_EXHAUSTED", NOW.minusHours(2), null)));
        Set<Metric> unchecked = EnumSet.noneOf(Metric.class);
        assertThat(service.collect(NOW, unchecked)).isEmpty();
        assertThat(unchecked).containsExactly(Metric.DB_POOL_EXHAUSTED);
        service.runAlerts();
        verify(repository, never()).normalize(any(), any());
    }

    // --- borders

    @Test
    void errorRateTenDoesNotFireTenPointOneDoes() {
        when(snapshots.totalsSince(NOW.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(60, 1000, 100, 0));
        assertThat(collect()).doesNotContainKey("ERROR_RATE");
        when(snapshots.totalsSince(NOW.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(60, 1000, 101, 0));
        assertThat(collect().get("ERROR_RATE").value()).isEqualByComparingTo("10.1");
    }

    @Test
    void errorRateAndResponseTimeNeedOneHundredRequests() {
        when(snapshots.totalsSince(NOW.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(60, 99, 99, 99 * 9000.0));
        assertThat(collect()).isEmpty();
        when(snapshots.totalsSince(NOW.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(60, 100, 100, 100 * 9000.0));
        assertThat(collect()).containsKeys("ERROR_RATE", "RESPONSE_TIME");
    }

    @Test
    void responseTimeFiveThousandDoesNotFireAboveDoes() {
        when(snapshots.totalsSince(NOW.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(60, 1000, 0, 5_000_000));
        assertThat(collect()).isEmpty();
        when(snapshots.totalsSince(NOW.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(60, 1000, 0, 5_000_100));
        assertThat(collect().get("RESPONSE_TIME").value()).isEqualByComparingTo("5000.1");
    }

    @Test
    void uptimeNinetyNineDoesNotFireBelowDoes() {
        when(snapshots.uptimePercent(Duration.ofDays(30))).thenReturn(99.0);
        assertThat(collect()).isEmpty();
        when(snapshots.uptimePercent(Duration.ofDays(30))).thenReturn(98.9);
        assertThat(collect().get("UPTIME").value()).isEqualByComparingTo("98.9");
    }

    @Test
    void importErrorRateBorderAndMinimumSample() {
        when(imports.countByBank(any(), any(), any())).thenReturn(List.of(importCounts("ITAU", 900, 100)));
        assertThat(collect()).isEmpty();
        when(imports.countByBank(any(), any(), any())).thenReturn(List.of(importCounts("ITAU", 899, 101)));
        assertThat(collect()).containsOnlyKeys("IMPORT_ERROR_RATE:ITAU");
        when(imports.countByBank(any(), any(), any())).thenReturn(List.of(importCounts("ITAU", 5, 5)));
        assertThat(collect()).containsOnlyKeys("IMPORT_ERROR_RATE:ITAU");
    }

    @Test
    void aiAccuracyBorderAndMinimumSample() {
        when(ai.countByBank(any(), any(), any())).thenReturn(List.of(aiCounts("NUBANK", 1000, 250)));
        assertThat(collect()).isEmpty();
        when(ai.countByBank(any(), any(), any())).thenReturn(List.of(aiCounts("NUBANK", 1000, 251)));
        Violation v = collect().get("AI_ACCURACY:NUBANK");
        assertThat(v.value()).isEqualByComparingTo("74.9");
        assertThat(v.threshold()).isEqualByComparingTo("75");
        when(ai.countByBank(any(), any(), any())).thenReturn(List.of(aiCounts("NUBANK", 20, 20)));
        assertThat(collect()).containsOnlyKeys("AI_ACCURACY:NUBANK");
    }

    @Test
    void zeroSessionsFireOnlyWithUser2xxTrafficInTheLastHour() {
        when(sessions.countActive(any(), any())).thenReturn(0L);
        when(snapshots.totalsSince(NOW.minusHours(1))).thenReturn(new HealthSnapshotService.Totals(60, 0, 0, 0, 0));
        assertThat(collect()).isEmpty();
        // bots (401/404) and the Stripe webhook: requests but no ok_requests
        when(snapshots.totalsSince(NOW.minusHours(1))).thenReturn(new HealthSnapshotService.Totals(60, 500, 0, 0, 0));
        assertThat(collect()).isEmpty();
        when(snapshots.totalsSince(NOW.minusHours(1))).thenReturn(new HealthSnapshotService.Totals(60, 500, 0, 0, 1));
        assertThat(collect()).containsOnlyKeys("ACTIVE_SESSIONS");
        when(sessions.countActive(any(), any())).thenReturn(1L);
        assertThat(collect()).isEmpty();
    }

    @Test
    void poolFiresWithTheCriterionOfThePanel() {
        when(adminService.pool()).thenReturn(new AdminService.Pool(9, 1, 10, 0, AdminService.poolStatus(9, 10, 0)));
        assertThat(collect()).isEmpty();
        when(adminService.pool()).thenReturn(new AdminService.Pool(10, 0, 10, 0, AdminService.poolStatus(10, 10, 0)));
        assertThat(collect()).containsOnlyKeys("DB_POOL_EXHAUSTED");
        when(adminService.pool()).thenReturn(new AdminService.Pool(5, 0, 10, 2, AdminService.poolStatus(5, 10, 2)));
        assertThat(collect().get("DB_POOL_EXHAUSTED").value()).isEqualByComparingTo("7");
        // the panel's database() runs SQL: the alert never calls it
        verify(adminService, never()).database();
    }

    @Test
    void redisDownFires() {
        redisDown();
        assertThat(collect()).containsOnlyKeys("REDIS_DOWN");
    }

    @Test
    void emailTextHasBankLabelValueAndLimitInPtBr() {
        SystemAlert a = new SystemAlert("IMPORT_ERROR_RATE:ITAU", "IMPORT_ERROR_RATE", "ITAU", new BigDecimal("12.5"),
            BigDecimal.TEN, NOW);
        assertThat(SystemAlertService.describe(a, "L")).contains("Métrica: Taxa de erro de importação (24 h) - Itaú",
            "Valor atual: 12,5 %", "Limite: acima de 10 %", "19/11/2026 10:20", "Abrir no painel: L");
    }

    @Test
    void snapshotsFailingLeaveErrorRateAndResponseTimeUncheckedAndTheirEpisodeOpen() {
        when(snapshots.totalsSince(any())).thenThrow(new IllegalStateException("db"));
        Set<Metric> unchecked = EnumSet.noneOf(Metric.class);
        service.collect(NOW, unchecked);
        assertThat(unchecked).contains(Metric.ERROR_RATE, Metric.RESPONSE_TIME);

        when(repository.findByNormalizedAtIsNull()).thenReturn(List.of(alert("RESPONSE_TIME", NOW.minusHours(2), null)));
        service.runAlerts();
        verify(repository, never()).normalize(any(), any());
    }

    @Test
    void labelsOfEveryMetric() {
        BigDecimal ten = BigDecimal.TEN;
        assertThat(SystemAlertService.valueLabel(Metric.ERROR_RATE, new BigDecimal("12.3"), ten)).isEqualTo("12,3 %");
        assertThat(SystemAlertService.thresholdLabel(Metric.ERROR_RATE, ten)).isEqualTo("acima de 10 %");
        assertThat(SystemAlertService.valueLabel(Metric.UPTIME, new BigDecimal("98.9"), new BigDecimal("99"))).isEqualTo("98,9 %");
        assertThat(SystemAlertService.thresholdLabel(Metric.UPTIME, new BigDecimal("99"))).isEqualTo("abaixo de 99 %");
        assertThat(SystemAlertService.valueLabel(Metric.RESPONSE_TIME, new BigDecimal("5000"), null)).isEqualTo("5000 ms");
        assertThat(SystemAlertService.thresholdLabel(Metric.RESPONSE_TIME, new BigDecimal("5000"))).isEqualTo("acima de 5000 ms");
        assertThat(SystemAlertService.valueLabel(Metric.IMPORT_ERROR_RATE, new BigDecimal("10.1"), ten)).isEqualTo("10,1 %");
        assertThat(SystemAlertService.thresholdLabel(Metric.IMPORT_ERROR_RATE, ten)).isEqualTo("acima de 10 %");
        assertThat(SystemAlertService.valueLabel(Metric.AI_ACCURACY, new BigDecimal("74.9"), null)).isEqualTo("74,9 %");
        assertThat(SystemAlertService.thresholdLabel(Metric.AI_ACCURACY, new BigDecimal("75"))).isEqualTo("abaixo de 75 %");
        assertThat(SystemAlertService.valueLabel(Metric.ACTIVE_SESSIONS, BigDecimal.ZERO, BigDecimal.ONE))
            .isEqualTo("0 sessões, com requisições na última hora");
        assertThat(SystemAlertService.thresholdLabel(Metric.ACTIVE_SESSIONS, BigDecimal.ONE))
            .isEqualTo("pelo menos 1 sessão ativa quando há tráfego");
        assertThat(SystemAlertService.valueLabel(Metric.REDIS_DOWN, BigDecimal.ZERO, BigDecimal.ONE)).isEqualTo("fora do ar");
        assertThat(SystemAlertService.thresholdLabel(Metric.REDIS_DOWN, BigDecimal.ONE)).isEqualTo("no ar");
        assertThat(SystemAlertService.valueLabel(Metric.DB_POOL_EXHAUSTED, new BigDecimal("7"), ten))
            .isEqualTo("7 conexões em uso ou na fila, de 10");
        assertThat(SystemAlertService.thresholdLabel(Metric.DB_POOL_EXHAUSTED, ten))
            .isEqualTo("menos de 10 em uso e ninguém na fila");
    }

    @Test
    void openFailingForOneKeyStillOpensTheOthers() {
        redisDown();
        when(adminService.pool()).thenReturn(new AdminService.Pool(10, 0, 10, 0, Status.RED));
        when(repository.findFirstByAlertKeyOrderByTriggeredAtDesc("REDIS_DOWN")).thenThrow(new IllegalStateException("db"));
        service.runAlerts();
        verify(repository).insertIfNoOpenEpisode(eq("DB_POOL_EXHAUSTED"), any(), any(), any(), any(), any());
        verify(repository, never()).insertIfNoOpenEpisode(eq("REDIS_DOWN"), any(), any(), any(), any(), any());
        verify(email, times(2)).sendSystemAlert(any(), any(), any());
    }

    @Test
    void sendFailuresCountOneFailingAdminAmongTwo() {
        when(email.sendSystemAlert(eq("b@example.com"), any(), any())).thenReturn(false);
        SystemAlert alert = service.open(new Violation(Metric.REDIS_DOWN, null, BigDecimal.ZERO, BigDecimal.ONE), NOW);
        assertThat(service.notifyAdmins(alert)).isEqualTo(1);
    }

    private static TransactionImportRepository.BankCounts importCounts(String bank, long successes, long failures) {
        return new TransactionImportRepository.BankCounts() {
            public String getBank() { return bank; }
            public long getSuccesses() { return successes; }
            public long getFailures() { return failures; }
        };
    }

    private static AiClassificationHistoryRepository.BankCounts aiCounts(String bank, long classifications, long corrections) {
        return new AiClassificationHistoryRepository.BankCounts() {
            public String getBank() { return bank; }
            public long getClassifications() { return classifications; }
            public long getCorrections() { return corrections; }
        };
    }
}
