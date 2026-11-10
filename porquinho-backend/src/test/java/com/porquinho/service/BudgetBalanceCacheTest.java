package com.porquinho.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 6.4: the 30 s cache of spent/committed with Redis mocked. */
@ExtendWith(MockitoExtension.class)
class BudgetBalanceCacheTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private BudgetBalanceCache cache;
    private final UUID userId = UUID.randomUUID();
    private final UUID category = UUID.randomUUID();
    private final AtomicInteger loads = new AtomicInteger();
    private String key;

    @BeforeEach
    void setUp() {
        cache = new BudgetBalanceCache(redisTemplate);
        key = BudgetBalanceCache.KEY_PREFIX + userId;
    }

    private Supplier<BudgetBalanceCache.Sums> loader() {
        return () -> {
            loads.incrementAndGet();
            return new BudgetBalanceCache.Sums(Map.of(category, new BigDecimal("12.30")), null);
        };
    }

    @Test
    void aHitReturnsTheCachedBigDecimalsWithoutLoading() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<String, Object> cached = new HashMap<>();
        cached.put("spent", Map.of(category.toString(), "50.10"));
        cached.put("committed", Map.of(category.toString(), "7"));
        when(hashOperations.get(key, "2026-11")).thenReturn(cached);

        BudgetBalanceCache.Sums sums = cache.get(userId, MONTH, loader());

        assertThat(loads).hasValue(0);
        assertThat(sums.spent().get(category)).isEqualTo(new BigDecimal("50.10"));
        assertThat(sums.committed().get(category)).isEqualTo(new BigDecimal("7"));
    }

    @Test
    void aMissLoadsStoresAsStringsAndSetsTheTtlWhenTheHashIsNew() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.getExpire(key)).thenReturn(-1L);

        BudgetBalanceCache.Sums sums = cache.get(userId, MONTH, loader());

        assertThat(loads).hasValue(1);
        assertThat(sums.spent().get(category)).isEqualByComparingTo("12.30");
        Map<String, Object> stored = new HashMap<>();
        stored.put("spent", Map.of(category.toString(), "12.30"));
        stored.put("committed", null);
        verify(hashOperations).put(key, "2026-11", stored);
        verify(redisTemplate).expire(key, BudgetBalanceCache.TTL);
    }

    @Test
    void aMissInAnExistingHashKeepsItsTtl() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.getExpire(key)).thenReturn(12L);

        cache.get(userId, MONTH, loader());

        verify(redisTemplate, never()).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    void redisDownFallsBackToTheLoader() {
        when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException("refused"));

        BudgetBalanceCache.Sums sums = cache.get(userId, MONTH, loader());

        assertThat(loads).hasValue(1);
        assertThat(sums.spent().get(category)).isEqualByComparingTo("12.30");
    }

    @Test
    void evictWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict(userId);
            verify(redisTemplate, never()).delete(anyString());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(redisTemplate).delete(key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictWithoutATransactionIsImmediateAndOnlyLogsWhenRedisIsDown() {
        doThrow(new RedisConnectionFailureException("refused")).when(redisTemplate).delete(eq(key));

        cache.evict(userId);

        verify(redisTemplate).delete(key);
    }
}
