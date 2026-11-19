package com.porquinho.service;

import com.porquinho.entity.SystemHealthSnapshot;
import com.porquinho.repository.SystemHealthSnapshotRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthSnapshotServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:30Z");
    private static final OffsetDateTime NOW_UTC = NOW.atOffset(ZoneOffset.UTC);

    private SystemHealthSnapshotRepository repository;
    private SimpleMeterRegistry registry;
    private HealthSnapshotService service;

    @BeforeEach
    void setUp() {
        repository = mock(SystemHealthSnapshotRepository.class);
        registry = new SimpleMeterRegistry();
        service = new HealthSnapshotService(repository, registry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void request(String uri, String status, long millis) {
        Timer.builder("http.server.requests").tags("method", "GET", "uri", uri, "status", status)
            .register(registry).record(Duration.ofMillis(millis));
    }

    private List<SystemHealthSnapshot> saved(int expectedCalls) {
        ArgumentCaptor<SystemHealthSnapshot> captor = ArgumentCaptor.forClass(SystemHealthSnapshot.class);
        verify(repository, times(expectedCalls)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void snapshotHoldsTheDeltaSinceThePreviousRunAndLeavesTheActuatorOut() {
        request("/api/v1/accounts", "200", 100);
        request("/api/v1/accounts", "500", 300);
        request("/actuator/health", "200", 5);
        request("/actuator/health", "503", 5);
        service.recordSnapshot();

        request("/api/v1/accounts", "200", 50);
        service.recordSnapshot();

        List<SystemHealthSnapshot> rows = saved(2);
        assertThat(rows.get(0).getRequestCount()).isEqualTo(2);
        assertThat(rows.get(0).getErrorCount()).isEqualTo(1);
        assertThat(rows.get(0).getTotalTimeMs()).isEqualTo(400.0);
        assertThat(rows.get(0).getRecordedAt()).isEqualTo(NOW_UTC);
        assertThat(rows.get(1).getRequestCount()).isEqualTo(1);
        assertThat(rows.get(1).getErrorCount()).isZero();
        assertThat(rows.get(1).getTotalTimeMs()).isEqualTo(50.0);
    }

    @Test
    void okRequestsAreThe2xxOutsideTheActuatorAndTheWebhooks() {
        request("/api/v1/accounts", "200", 10);
        request("/api/v1/accounts", "201", 10);
        request("/api/v1/accounts", "401", 10);
        request("/**", "404", 10);
        request("/api/v1/webhooks/stripe", "200", 10);
        request("/actuator/health", "200", 10);
        service.recordSnapshot();
        request("/api/v1/accounts", "200", 10);
        service.recordSnapshot();

        List<SystemHealthSnapshot> rows = saved(2);
        assertThat(rows.get(0).getRequestCount()).isEqualTo(5);
        assertThat(rows.get(0).getOkRequests()).isEqualTo(2);
        assertThat(rows.get(1).getOkRequests()).isEqualTo(1);
    }

    @Test
    void countersResetNeverGiveANegativeDelta() {
        request("/api/v1/accounts", "500", 100);
        request("/api/v1/accounts", "500", 100);
        service.recordSnapshot();

        // Restart-like: the meters start from zero again
        registry.clear();
        request("/api/v1/accounts", "500", 40);
        service.recordSnapshot();

        SystemHealthSnapshot after = saved(2).get(1);
        assertThat(after.getRequestCount()).isEqualTo(1);
        assertThat(after.getErrorCount()).isEqualTo(1);
        assertThat(after.getTotalTimeMs()).isEqualTo(40.0);
    }

    @Test
    void firstSnapshotAfterAStartCountsOnlyWhatCameSinceTheStart() {
        // A new instance (restart) with fresh meters: the old rows in the table stay, the first delta is the new traffic
        request("/api/v1/budgets", "200", 10);
        service.recordSnapshot();

        assertThat(saved(1).get(0).getRequestCount()).isEqualTo(1);
    }

    @Test
    void jobPurgesRowsOlderThan31Days() {
        service.recordSnapshot();
        verify(repository).deleteRecordedBefore(NOW_UTC.minusDays(31));
    }

    @Test
    void uptimeIsNullWithoutSnapshots() {
        when(repository.firstRecordedAt()).thenReturn(null);
        assertThat(service.uptimePercent(Duration.ofDays(30))).isNull();
    }

    @Test
    void uptimeIsTheShareOfMinutesWithASnapshotSinceTheFirstOne() {
        // First snapshot 99 minutes ago: 100 minute marks expected, 90 recorded
        OffsetDateTime first = NOW_UTC.minusMinutes(99);
        when(repository.firstRecordedAt()).thenReturn(first);
        when(repository.totalsSince(first)).thenReturn(List.<Object[]>of(new Object[]{90L, 0L, 0L, 0.0, 0L}));

        assertThat(service.uptimePercent(Duration.ofDays(30))).isEqualTo(90.0);
    }

    @Test
    void uptimeUsesTheWholeWindowWhenDataIsOlder() {
        when(repository.firstRecordedAt()).thenReturn(NOW_UTC.minusDays(40));
        // A full 24h window holds 1440 minute marks
        when(repository.totalsSince(any())).thenReturn(List.<Object[]>of(new Object[]{1440L, 0L, 0L, 0.0, 0L}));

        assertThat(service.uptimePercent(Duration.ofHours(24))).isEqualTo(100.0);
        verify(repository).totalsSince(NOW_UTC.minusHours(24));
    }

    @Test
    void oneMissingMinuteInAFullWindowIsBelow100() {
        when(repository.firstRecordedAt()).thenReturn(NOW_UTC.minusDays(40));
        when(repository.totalsSince(any())).thenReturn(List.<Object[]>of(new Object[]{1439L, 0L, 0L, 0.0, 0L}));

        assertThat(service.uptimePercent(Duration.ofHours(24))).isEqualTo(1439 * 100.0 / 1440);
    }

    @Test
    void failedSaveKeepsTheTrafficForTheNextRun() {
        request("/api/v1/accounts", "500", 100);
        when(repository.save(any())).thenThrow(new RuntimeException("db down")).thenAnswer(i -> i.getArgument(0));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.recordSnapshot()).hasMessage("db down");
        request("/api/v1/accounts", "200", 50);
        service.recordSnapshot();

        SystemHealthSnapshot second = saved(2).get(1);
        assertThat(second.getRequestCount()).isEqualTo(2);
        assertThat(second.getErrorCount()).isEqualTo(1);
        assertThat(second.getTotalTimeMs()).isEqualTo(150.0);
    }

    @Test
    void failedPurgeKeepsTheSavedRowAndTheNextDeltaIsExact() {
        request("/api/v1/accounts", "500", 100);
        when(repository.deleteRecordedBefore(any())).thenThrow(new RuntimeException("purge failed")).thenReturn(0);

        service.recordSnapshot();
        request("/api/v1/accounts", "200", 50);
        service.recordSnapshot();

        List<SystemHealthSnapshot> rows = saved(2);
        assertThat(rows.get(0).getRequestCount()).isEqualTo(1);
        assertThat(rows.get(0).getErrorCount()).isEqualTo(1);
        assertThat(rows.get(1).getRequestCount()).isEqualTo(1);
        assertThat(rows.get(1).getErrorCount()).isZero();
        assertThat(rows.get(1).getTotalTimeMs()).isEqualTo(50.0);
    }

    @Test
    void lastDayBucketsTheSameWithSnapshotsAtMinus3() {
        OffsetDateTime currentHour = NOW_UTC.withMinute(0).withSecond(0);
        ZoneOffset sp = ZoneOffset.ofHours(-3);
        when(repository.findByRecordedAtGreaterThanEqualOrderByRecordedAt(currentHour.minusHours(23))).thenReturn(List.of(
            new SystemHealthSnapshot(currentHour.withOffsetSameInstant(sp), 2, 1, 300),
            new SystemHealthSnapshot(currentHour.plusMinutes(1).withOffsetSameInstant(sp), 2, 0, 100)));

        List<HealthSnapshotService.HourTotals> hours = service.lastDayByHour();

        assertThat(hours.get(22)).isEqualTo(new HealthSnapshotService.HourTotals(currentHour.minusHours(1), 2, 1, 150.0));
        assertThat(hours.get(23)).isEqualTo(new HealthSnapshotService.HourTotals(currentHour, 2, 0, 50.0));
    }

    @Test
    void totalsWithoutRowsAreZero() {
        when(repository.totalsSince(any())).thenReturn(List.<Object[]>of(new Object[]{0L, null, null, null, null}));

        assertThat(service.totalsSince(NOW_UTC)).isEqualTo(new HealthSnapshotService.Totals(0, 0, 0, 0));
    }

    @Test
    void lastDayHasOneEntryPerHourWithEmptyHoursAtZero() {
        OffsetDateTime currentHour = NOW_UTC.withMinute(0).withSecond(0);
        when(repository.findByRecordedAtGreaterThanEqualOrderByRecordedAt(currentHour.minusHours(23))).thenReturn(List.of(
            // HH:00 holds the traffic of HH-1:59: it belongs to the previous hour
            new SystemHealthSnapshot(currentHour, 2, 1, 300),
            new SystemHealthSnapshot(currentHour.plusMinutes(1), 2, 0, 100),
            new SystemHealthSnapshot(currentHour.plusMinutes(2), 2, 0, 300)));

        List<HealthSnapshotService.HourTotals> hours = service.lastDayByHour();

        assertThat(hours).hasSize(24);
        assertThat(hours.get(0)).isEqualTo(new HealthSnapshotService.HourTotals(currentHour.minusHours(23), 0, 0, null));
        assertThat(hours.get(22)).isEqualTo(new HealthSnapshotService.HourTotals(currentHour.minusHours(1), 2, 1, 150.0));
        assertThat(hours.get(23)).isEqualTo(new HealthSnapshotService.HourTotals(currentHour, 4, 0, 100.0));
    }
}
