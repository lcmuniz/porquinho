package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.exception.ForbiddenException;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private UserRepository userRepository;
    private UserSessionRepository userSessionRepository;
    private HealthSnapshotService snapshots;
    private SimpleMeterRegistry registry;
    private HikariPoolMXBean pool;
    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redis;
    private AdminService service;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = mock(UserRepository.class);
        userSessionRepository = mock(UserSessionRepository.class);
        snapshots = mock(HealthSnapshotService.class);
        registry = new SimpleMeterRegistry();
        DataSource dataSource = mock(DataSource.class);
        HikariDataSource hikari = mock(HikariDataSource.class);
        pool = mock(HikariPoolMXBean.class);
        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikari);
        when(hikari.getHikariPoolMXBean()).thenReturn(pool);
        when(hikari.getMaximumPoolSize()).thenReturn(20);
        jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        redis = mock(StringRedisTemplate.class);
        when(snapshots.totalsSince(any())).thenReturn(new HealthSnapshotService.Totals(0, 0, 0, 0));
        when(snapshots.lastDayByHour()).thenReturn(List.of());
        service = new AdminService(userRepository, userSessionRepository, snapshots, registry, dataSource,
            jdbcTemplate, redis, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private void redisInfo(Properties info) {
        when(redis.execute(any(RedisCallback.class))).thenReturn(info);
    }

    @Test
    void requireAdminRefusesNonAdmins() {
        UUID admin = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        when(userRepository.existsByIdAndAdminTrue(admin)).thenReturn(true);

        service.requireAdmin(admin);
        assertThatThrownBy(() -> service.requireAdmin(user)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void colorLimits() {
        assertThat(AdminService.uptimeStatus(null)).isEqualTo(Status.UNKNOWN);
        assertThat(AdminService.uptimeStatus(99.5)).isEqualTo(Status.GREEN);
        assertThat(AdminService.uptimeStatus(99.49)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.uptimeStatus(99.0)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.uptimeStatus(98.99)).isEqualTo(Status.RED);

        assertThat(AdminService.errorRateStatus(null)).isEqualTo(Status.UNKNOWN);
        assertThat(AdminService.errorRateStatus(0.99)).isEqualTo(Status.GREEN);
        assertThat(AdminService.errorRateStatus(1.0)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.errorRateStatus(10.0)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.errorRateStatus(10.01)).isEqualTo(Status.RED);

        assertThat(AdminService.responseTimeStatus(null)).isEqualTo(Status.UNKNOWN);
        assertThat(AdminService.responseTimeStatus(999.0)).isEqualTo(Status.GREEN);
        assertThat(AdminService.responseTimeStatus(1000.0)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.responseTimeStatus(5000.0)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.responseTimeStatus(5000.1)).isEqualTo(Status.RED);

        assertThat(AdminService.poolStatus(15, 20, 0)).isEqualTo(Status.GREEN);
        assertThat(AdminService.poolStatus(16, 20, 0)).isEqualTo(Status.YELLOW);
        assertThat(AdminService.poolStatus(20, 20, 0)).isEqualTo(Status.RED);
        assertThat(AdminService.poolStatus(3, 20, 1)).isEqualTo(Status.RED);
    }

    @Test
    void withoutSnapshotsUptimeIsUnknownAndTheRestStillComes() {
        when(snapshots.uptimePercent(any())).thenReturn(null);
        Properties info = new Properties();
        info.setProperty("keyspace_hits", "75");
        info.setProperty("keyspace_misses", "25");
        info.setProperty("used_memory", "1048576");
        info.setProperty("maxmemory", "0");
        redisInfo(info);
        when(pool.getActiveConnections()).thenReturn(2);
        when(pool.getIdleConnections()).thenReturn(3);
        when(userSessionRepository.countActive(any(), any())).thenReturn(4L);

        AdminHealthResponse health = service.health();

        assertThat(health.uptime().last24h()).isNull();
        assertThat(health.uptime().last30d()).isNull();
        assertThat(health.uptime().status()).isEqualTo(Status.UNKNOWN);
        assertThat(health.errors().errorRate24h()).isNull();
        assertThat(health.responseTime().status()).isEqualTo(Status.UNKNOWN);
        assertThat(health.redis()).isEqualTo(new AdminHealthResponse.Redis(true, 75.0, 1048576L, null, Status.GREEN));
        assertThat(health.database().active()).isEqualTo(2);
        assertThat(health.database().poolStatus()).isEqualTo(Status.GREEN);
        assertThat(health.sessions()).isEqualTo(new AdminHealthResponse.Sessions(4, Status.GREEN));
        assertThat(health.generatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisDownIsRedAndThePayloadStillComes() {
        when(redis.execute(any(RedisCallback.class))).thenThrow(new RedisConnectionFailureException("down"));
        when(snapshots.uptimePercent(any())).thenReturn(100.0);

        AdminHealthResponse health = service.health();

        assertThat(health.redis()).isEqualTo(new AdminHealthResponse.Redis(false, null, null, null, Status.RED));
        assertThat(health.uptime().status()).isEqualTo(Status.GREEN);
        assertThat(health.sessions().status()).isEqualTo(Status.YELLOW);
    }

    @Test
    void errorsAndResponseTimeComeFromTheSnapshotWindows() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(snapshots.totalsSince(now.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(1440, 200, 30, 400_000));
        when(snapshots.totalsSince(now.minusDays(7))).thenReturn(new HealthSnapshotService.Totals(0, 0, 40, 0));
        when(snapshots.totalsSince(now.minusDays(30))).thenReturn(new HealthSnapshotService.Totals(0, 0, 50, 0));
        redisInfo(new Properties());

        AdminHealthResponse health = service.health();

        assertThat(health.errors()).isEqualTo(new AdminHealthResponse.Errors(30, 40, 50, 15.0, Status.RED));
        assertThat(health.responseTime()).isEqualTo(new AdminHealthResponse.ResponseTime(2000.0, Status.YELLOW));
    }

    @Test
    void uptimeKeeps24hAnd30dApartAndTheColorFollows30d() {
        when(snapshots.uptimePercent(Duration.ofHours(24))).thenReturn(100.0);
        when(snapshots.uptimePercent(Duration.ofDays(30))).thenReturn(98.0);
        redisInfo(new Properties());

        AdminHealthResponse.Uptime uptime = service.health().uptime();

        assertThat(uptime.last24h()).isEqualTo(100.0);
        assertThat(uptime.last30d()).isEqualTo(98.0);
        assertThat(uptime.status()).isEqualTo(Status.RED);
    }

    @Test
    void valuesAreRoundedToOneDecimalBeforeTheColorAsTheAlertDoes() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        // 10.04 % and 98.96 % show "10 %" and "99 %": yellow on the panel, no alert
        when(snapshots.totalsSince(now.minusHours(24))).thenReturn(new HealthSnapshotService.Totals(1440, 10_000, 1004, 50_000_400));
        when(snapshots.totalsSince(now.minusDays(7))).thenReturn(new HealthSnapshotService.Totals(0, 0, 0, 0));
        when(snapshots.totalsSince(now.minusDays(30))).thenReturn(new HealthSnapshotService.Totals(0, 0, 0, 0));
        when(snapshots.uptimePercent(Duration.ofHours(24))).thenReturn(99.94);
        when(snapshots.uptimePercent(Duration.ofDays(30))).thenReturn(98.96);
        redisInfo(new Properties());

        AdminHealthResponse health = service.health();

        assertThat(health.errors().errorRate24h()).isEqualTo(10.0);
        assertThat(health.errors().status()).isEqualTo(Status.YELLOW);
        assertThat(health.responseTime()).isEqualTo(new AdminHealthResponse.ResponseTime(5000.0, Status.YELLOW));
        assertThat(health.uptime().last24h()).isEqualTo(99.9);
        assertThat(health.uptime().last30d()).isEqualTo(99.0);
        assertThat(health.uptime().status()).isEqualTo(Status.YELLOW);
    }

    @Test
    void hourlyComesFromTheSnapshots() {
        OffsetDateTime hour = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(snapshots.lastDayByHour()).thenReturn(List.of(new HealthSnapshotService.HourTotals(hour, 10, 2, 50.0)));
        redisInfo(new Properties());

        assertThat(service.health().hourly()).containsExactly(new AdminHealthResponse.HourPoint(hour, 10, 2, 50.0));
    }

    @Test
    void endpointsLeaveTheActuatorOutAndComeSlowestFirst() {
        record(registry, "GET", "/api/v1/accounts", "200", 100);
        record(registry, "GET", "/api/v1/accounts", "500", 300);
        record(registry, "POST", "/api/v1/imports", "201", 1500);
        record(registry, "GET", "/actuator/health", "200", 5000);

        List<AdminHealthResponse.Endpoint> endpoints = service.endpoints();

        assertThat(endpoints).containsExactly(
            new AdminHealthResponse.Endpoint("POST", "/api/v1/imports", 1, 1500.0, 0, Status.YELLOW),
            new AdminHealthResponse.Endpoint("GET", "/api/v1/accounts", 2, 200.0, 1, Status.GREEN));
    }

    private static void record(SimpleMeterRegistry registry, String method, String uri, String status, long millis) {
        Timer.builder("http.server.requests").tags("method", method, "uri", uri, "status", status)
            .register(registry).record(Duration.ofMillis(millis));
    }

    @Test
    void poolReadsOnlyTheHikariMxBean() {
        when(pool.getActiveConnections()).thenReturn(20);
        when(pool.getIdleConnections()).thenReturn(0);
        when(pool.getThreadsAwaitingConnection()).thenReturn(3);

        AdminService.Pool p = service.pool();

        assertThat(p).isEqualTo(new AdminService.Pool(20, 0, 20, 3, Status.RED));
        // Story 10.5: with the pool exhausted a query would wait for a connection and the alert would never fire
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }
}
