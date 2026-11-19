package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse;
import com.porquinho.dto.AdminHealthResponse.Database;
import com.porquinho.dto.AdminHealthResponse.Endpoint;
import com.porquinho.dto.AdminHealthResponse.Errors;
import com.porquinho.dto.AdminHealthResponse.HourPoint;
import com.porquinho.dto.AdminHealthResponse.Redis;
import com.porquinho.dto.AdminHealthResponse.ResponseTime;
import com.porquinho.dto.AdminHealthResponse.Sessions;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.dto.AdminHealthResponse.Uptime;
import com.porquinho.exception.ForbiddenException;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Story 10.1: the admin role check and the system health of the panel. The numbers come from in-process Micrometer
 * (the nginx blocks /actuator from outside), Hikari, Redis INFO, user_sessions and the per-minute snapshots.
 * The red limits are the ones of the 10.5 alerts.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    public static final String FORBIDDEN_MESSAGE = "Esta área é só para administradores.";
    static final double UPTIME_TARGET = 99.5;
    static final Duration ACTIVE_SESSION_WINDOW = Duration.ofMinutes(30);
    static final String SLOW_QUERIES_SQL = "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() "
        + "AND pid <> pg_backend_pid() AND backend_type = 'client backend' AND usename = current_user "
        + "AND state = 'active' AND now() - query_start > interval '1 second'";

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final HealthSnapshotService snapshots;
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final Clock clock;

    public AdminService(UserRepository userRepository, UserSessionRepository userSessionRepository,
                        HealthSnapshotService snapshots, MeterRegistry meterRegistry, DataSource dataSource,
                        JdbcTemplate jdbcTemplate, StringRedisTemplate redis, Clock clock) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.snapshots = snapshots;
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.clock = clock;
    }

    public boolean isAdmin(UUID userId) {
        return userRepository.existsByIdAndAdminTrue(userId);
    }

    public void requireAdmin(UUID userId) {
        if (!isAdmin(userId)) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }

    public AdminHealthResponse health() {
        OffsetDateTime now = OffsetDateTime.now(clock);

        Double uptime24h = snapshots.uptimePercent(Duration.ofHours(24));
        Double uptime30d = snapshots.uptimePercent(Duration.ofDays(30));
        Uptime uptime = new Uptime(uptime24h, uptime30d, UPTIME_TARGET, uptimeStatus(uptime30d));

        HealthSnapshotService.Totals day = snapshots.totalsSince(now.minusHours(24));
        HealthSnapshotService.Totals week = snapshots.totalsSince(now.minusDays(7));
        HealthSnapshotService.Totals month = snapshots.totalsSince(now.minusDays(30));
        Double errorRate = day.requests() == 0 ? null : day.errors() * 100.0 / day.requests();
        Errors errors = new Errors(day.errors(), week.errors(), month.errors(), errorRate, errorRateStatus(errorRate));

        Double avgMs = day.requests() == 0 ? null : day.totalTimeMs() / day.requests();
        ResponseTime responseTime = new ResponseTime(avgMs, responseTimeStatus(avgMs));

        List<HourPoint> hourly = snapshots.lastDayByHour().stream()
            .map(h -> new HourPoint(h.hour(), h.requests(), h.errors(), h.avgMs()))
            .toList();

        long active = userSessionRepository.countActive(now, now.minus(ACTIVE_SESSION_WINDOW));
        Sessions sessions = new Sessions(active, active > 0 ? Status.GREEN : Status.YELLOW);

        return new AdminHealthResponse(now, uptime, errors, responseTime, endpoints(), hourly, database(), redis(), sessions);
    }

    /** Per method and URI template, outside /actuator, since the last start; the slowest first. */
    List<Endpoint> endpoints() {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, Double> times = new LinkedHashMap<>();
        for (Timer timer : meterRegistry.find(HealthSnapshotService.HTTP_METRIC).timers()) {
            if (!HealthSnapshotService.counted(timer)) continue;
            String key = timer.getId().getTag("method") + " " + timer.getId().getTag("uri");
            long[] c = counts.computeIfAbsent(key, k -> new long[2]);
            c[0] += timer.count();
            if (HealthSnapshotService.isServerError(timer)) c[1] += timer.count();
            times.merge(key, timer.totalTime(TimeUnit.MILLISECONDS), Double::sum);
        }
        List<Endpoint> result = new ArrayList<>();
        counts.forEach((key, c) -> {
            if (c[0] == 0) return;
            int space = key.indexOf(' ');
            double avgMs = times.get(key) / c[0];
            result.add(new Endpoint(key.substring(0, space), key.substring(space + 1), c[0], avgMs, c[1],
                responseTimeStatus(avgMs)));
        });
        result.sort(Comparator.comparingDouble(Endpoint::avgMs).reversed());
        return result;
    }

    /** Hikari pool right now. */
    record Pool(int active, int idle, int max, int waiting, Status status) {
    }

    /** From the Hikari MXBean only, no SQL: still answers with the pool exhausted (the 10.5 alert). */
    Pool pool() {
        HikariPoolMXBean pool;
        int max;
        try {
            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            pool = hikari.getHikariPoolMXBean();
            max = hikari.getMaximumPoolSize();
        } catch (SQLException e) {
            throw new IllegalStateException("The datasource is not a Hikari pool", e);
        }
        int active = pool.getActiveConnections();
        int waiting = pool.getThreadsAwaitingConnection();
        return new Pool(active, pool.getIdleConnections(), max, waiting, poolStatus(active, max, waiting));
    }

    Database database() {
        Pool pool = pool();
        Long slow = jdbcTemplate.queryForObject(SLOW_QUERIES_SQL, Long.class);
        long slowQueries = slow == null ? 0 : slow;
        return new Database(pool.active(), pool.idle(), pool.max(), pool.waiting(), pool.status(),
            slowQueries, slowQueries == 0 ? Status.GREEN : Status.YELLOW);
    }

    /** Redis down (or INFO failing) never breaks the panel: it shows red "fora do ar". */
    Redis redis() {
        try {
            Properties info = redis.execute((RedisCallback<Properties>) c -> c.serverCommands().info());
            if (info == null) throw new IllegalStateException("empty INFO");
            Long hits = longValue(info, "keyspace_hits");
            Long misses = longValue(info, "keyspace_misses");
            Double hitRate = hits == null || misses == null || hits + misses == 0 ? null : hits * 100.0 / (hits + misses);
            Long max = longValue(info, "maxmemory");
            return new Redis(true, hitRate, longValue(info, "used_memory"), max == null || max == 0 ? null : max, Status.GREEN);
        } catch (RuntimeException e) {
            log.warn("Redis INFO failed for the admin panel: {}", e.getMessage());
            return new Redis(false, null, null, null, Status.RED);
        }
    }

    static Status uptimeStatus(Double percent) {
        if (percent == null) return Status.UNKNOWN;
        if (percent >= UPTIME_TARGET) return Status.GREEN;
        return percent >= 99.0 ? Status.YELLOW : Status.RED;
    }

    static Status errorRateStatus(Double percent) {
        if (percent == null) return Status.UNKNOWN;
        if (percent < 1.0) return Status.GREEN;
        return percent <= 10.0 ? Status.YELLOW : Status.RED;
    }

    static Status responseTimeStatus(Double avgMs) {
        if (avgMs == null) return Status.UNKNOWN;
        if (avgMs < 1000) return Status.GREEN;
        return avgMs <= 5000 ? Status.YELLOW : Status.RED;
    }

    static Status poolStatus(int active, int max, int waiting) {
        if (waiting > 0 || active >= max) return Status.RED;
        return active >= max * 0.8 ? Status.YELLOW : Status.GREEN;
    }

    private static Long longValue(Properties info, String key) {
        String value = info.getProperty(key);
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
