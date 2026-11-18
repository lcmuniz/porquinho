package com.porquinho.service;

import com.porquinho.entity.SystemHealthSnapshot;
import com.porquinho.repository.SystemHealthSnapshotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Story 10.1: every minute, records the delta of the {@code http.server.requests} counters (outside /actuator) since
 * the previous run, so the uptime and the 24h/7d/30d errors survive a restart (the Micrometer counters start at zero).
 * Single instance, no ShedLock (like BudgetAlertService). Rows older than 31 days are deleted by the same job.
 */
@Service
public class HealthSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(HealthSnapshotService.class);

    static final String HTTP_METRIC = "http.server.requests";
    static final Duration RETENTION = Duration.ofDays(31);

    /** Sums of a window. {@code minutes} is the number of snapshots (one per minute the backend was up). */
    public record Totals(long minutes, long requests, long errors, double totalTimeMs) {
    }

    /** One hour of the last 24h: start of the hour (UTC), requests, 5xx and the average time (null without requests). */
    public record HourTotals(OffsetDateTime hour, long requests, long errors, Double avgMs) {
    }

    private final SystemHealthSnapshotRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    // In-memory counters of the previous run: zero at boot, so the first snapshot holds what came since the start
    private long lastRequests;
    private long lastErrors;
    private double lastTotalMs;

    public HealthSnapshotService(SystemHealthSnapshotRepository repository, MeterRegistry meterRegistry, Clock clock) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /** True for the requests the panel counts: the actuator (healthcheck, metrics) stays out. */
    static boolean counted(Timer timer) {
        String uri = timer.getId().getTag("uri");
        return uri == null || !uri.startsWith("/actuator");
    }

    static boolean isServerError(Timer timer) {
        String status = timer.getId().getTag("status");
        return status != null && status.startsWith("5");
    }

    /** Not @Transactional: the save commits on its own before the counters move, and the purge runs apart. */
    public synchronized void recordSnapshot() {
        long requests = 0;
        long errors = 0;
        double totalMs = 0;
        for (Timer timer : meterRegistry.find(HTTP_METRIC).timers()) {
            if (!counted(timer)) continue;
            requests += timer.count();
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            if (isServerError(timer)) errors += timer.count();
        }
        // A counter below the previous value was reset (meters cleared): what it holds now is all new
        long deltaRequests = requests >= lastRequests ? requests - lastRequests : requests;
        long deltaErrors = errors >= lastErrors ? errors - lastErrors : errors;
        double deltaMs = totalMs >= lastTotalMs ? totalMs - lastTotalMs : totalMs;

        OffsetDateTime now = OffsetDateTime.now(clock);
        repository.save(new SystemHealthSnapshot(now, deltaRequests, deltaErrors, deltaMs));
        // Only after the save: a failed save keeps the minute's traffic for the next run
        lastRequests = requests;
        lastErrors = errors;
        lastTotalMs = totalMs;
        try {
            repository.deleteRecordedBefore(now.minus(RETENTION));
        } catch (RuntimeException e) {
            // The snapshot is saved: the next run retries the purge
            log.warn("Health snapshot purge failed: {}", e.getMessage());
        }
    }

    public Totals totalsSince(OffsetDateTime from) {
        Object[] row = repository.totalsSince(from).get(0);
        return new Totals(number(row[0]), number(row[1]), number(row[2]),
            row[3] == null ? 0 : ((Number) row[3]).doubleValue());
    }

    /**
     * Percentage of the minutes of the window that have a snapshot. The window starts at the first snapshot when it is
     * more recent (a fresh install is not "down" before it existed). Null when there is no snapshot yet.
     */
    public Double uptimePercent(Duration window) {
        OffsetDateTime first = repository.firstRecordedAt();
        if (first == null) return null;
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime start = now.minus(window);
        // Minute marks on minute-truncated instants: (start, now] for a full window, [first, now] from the first snapshot
        long expected;
        if (first.isAfter(start)) {
            start = first;
            expected = minutesBetween(start, now) + 1;
        } else {
            expected = minutesBetween(start, now);
        }
        long recorded = totalsSince(start).minutes();
        return Math.min(100.0, recorded * 100.0 / expected);
    }

    /** The last 24 hours, one entry per hour (oldest first), hours without a snapshot at zero. */
    public List<HourTotals> lastDayByHour() {
        OffsetDateTime currentHour = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime from = currentHour.minusHours(23);
        Map<OffsetDateTime, long[]> counts = new LinkedHashMap<>();
        Map<OffsetDateTime, Double> times = new LinkedHashMap<>();
        for (int i = 0; i < 24; i++) {
            counts.put(from.plusHours(i), new long[2]);
            times.put(from.plusHours(i), 0.0);
        }
        for (SystemHealthSnapshot s : repository.findByRecordedAtGreaterThanEqualOrderByRecordedAt(from)) {
            // The snapshot of HH:00 holds the traffic of HH-1:59: bucket by the minute it covers
            OffsetDateTime hour = s.getRecordedAt().minusMinutes(1).withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS);
            long[] c = counts.get(hour);
            if (c == null) continue;
            c[0] += s.getRequestCount();
            c[1] += s.getErrorCount();
            times.merge(hour, s.getTotalTimeMs(), Double::sum);
        }
        List<HourTotals> result = new ArrayList<>(24);
        counts.forEach((hour, c) -> result.add(new HourTotals(hour, c[0], c[1], c[0] == 0 ? null : times.get(hour) / c[0])));
        return result;
    }

    private static long minutesBetween(OffsetDateTime from, OffsetDateTime to) {
        return Duration.between(from.truncatedTo(ChronoUnit.MINUTES), to.truncatedTo(ChronoUnit.MINUTES)).toMinutes();
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
