package com.porquinho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.service.DashboardCache.Layer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 7.7: the dashboard cache with Redis mocked. */
@ExtendWith(MockitoExtension.class)
class DashboardCacheTest {

    private static final FinancialHealthResponse HEALTH =
        new FinancialHealthResponse(FinancialHealthResponse.HealthStatus.ATTENTION, 3, 1, 1, 0, 0);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private HashOperations<String, Object, Object> hashes;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final UUID userId = UUID.randomUUID();
    private final AtomicInteger loads = new AtomicInteger();
    private DashboardCache cache;

    @BeforeEach
    void setUp() {
        cache = new DashboardCache(redis, objectMapper, meters);
    }

    private FinancialHealthResponse loadHealth() {
        loads.incrementAndGet();
        return HEALTH;
    }

    private double count(String layer, String result) {
        var counter = meters.find("dashboard.cache").tag("layer", layer).tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aHitComesBackAsTheSameDtoWithoutLoading() throws Exception {
        var layer1 = new DashboardLayer1Response(new BigDecimal("10.50"), List.of(), null, new BigDecimal("0.00"),
            null, new BigDecimal("-3.10"), List.of(new DashboardLayer1Response.WealthPoint(LocalDate.of(2026, 11, 1),
            new BigDecimal("7.00"))));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("dashboard:layer1:" + userId)).thenReturn(objectMapper.writeValueAsString(layer1));

        var cached = cache.get(Layer.LAYER1, userId, null, DashboardLayer1Response.class, () -> {
            loads.incrementAndGet();
            return null;
        });

        assertThat(loads).hasValue(0);
        assertThat(cached).isEqualTo(layer1);
        assertThat(cached.totalBalance().scale()).isEqualTo(2);
        assertThat(count("layer1", "hit")).isEqualTo(1);
    }

    @Test
    void aMissLoadsAndStoresWithTheLayerTtl() throws Exception {
        when(redis.opsForValue()).thenReturn(values);

        assertThat(cache.get(Layer.HEALTH, userId, null, FinancialHealthResponse.class, this::loadHealth))
            .isEqualTo(HEALTH);

        assertThat(loads).hasValue(1);
        verify(values).set("dashboard:health:" + userId, objectMapper.writeValueAsString(HEALTH),
            Duration.ofSeconds(30));
        assertThat(count("health", "miss")).isEqualTo(1);
    }

    @Test
    void aLayer3MissStoresAFieldAndSetsTheTtlOnlyOnANewHash() {
        String key = "dashboard:layer3:" + userId;
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.getExpire(key)).thenReturn(-1L, 100L);

        cache.get(Layer.LAYER3, userId, "abc", FinancialHealthResponse.class, this::loadHealth);
        cache.get(Layer.LAYER3, userId, "def", FinancialHealthResponse.class, this::loadHealth);

        verify(hashes).put(key, "abc", objectMapperWrite(HEALTH));
        verify(hashes).put(key, "def", objectMapperWrite(HEALTH));
        verify(redis).expire(key, Duration.ofMinutes(15));
        assertThat(count("layer3", "miss")).isEqualTo(2);
    }

    private String objectMapperWrite(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void redisDownOnGetFallsBackToTheLoader() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("refused"));

        assertThat(cache.get(Layer.HEALTH, userId, null, FinancialHealthResponse.class, this::loadHealth))
            .isEqualTo(HEALTH);
        assertThat(loads).hasValue(1);
        assertThat(count("health", "error")).isEqualTo(1);
        assertThat(count("health", "miss")).isZero();
    }

    @Test
    void aLoaderErrorPropagatesAndNothingIsStored() {
        when(redis.opsForHash()).thenReturn(hashes);

        // the 400 reaches the controller
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cache.get(Layer.LAYER3, userId, "abc",
            FinancialHealthResponse.class, () -> {
                throw new IllegalArgumentException("bad page");
            })).isInstanceOf(IllegalArgumentException.class);
        verify(hashes, never()).put(any(), any(), any());
    }

    @Test
    void evictAllWaitsForTheCommitAndClearsEveryLayer() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAll(userId);
            verify(redis, never()).delete(anyCollection());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(redis).delete(List.of("dashboard:health:" + userId, "dashboard:layer1:" + userId,
                "dashboard:layer2:" + userId, "dashboard:layer3:" + userId));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictBudgetLayersKeepsTheLayer3AndOnlyLogsWhenRedisIsDown() {
        List<String> keys = List.of("dashboard:health:" + userId, "dashboard:layer1:" + userId,
            "dashboard:layer2:" + userId);
        doThrow(new RedisConnectionFailureException("refused")).when(redis).delete(keys);

        cache.evictBudgetLayers(userId);

        verify(redis).delete(keys);
        verify(redis, never()).delete(anyString());
    }
}
