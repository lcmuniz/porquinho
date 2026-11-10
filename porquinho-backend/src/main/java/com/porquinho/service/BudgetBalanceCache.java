package com.porquinho.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Spent and committed per category of a user's month (Story 6.4), cached for 30 s in one hash per user,
 * {@code category:balance:{userId}} with field {@code YYYY-MM}. One hash per user (not one key per category, as the
 * epic says) so one DEL clears every month without SCAN. The TTL is set only when the hash is created, so no month
 * lives longer than 30 s. Redis down never breaks a read: it falls back to the loader and an evict only logs.
 */
@Component
public class BudgetBalanceCache {

    static final String KEY_PREFIX = "category:balance:";
    static final Duration TTL = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(BudgetBalanceCache.class);

    /** {@code committed} is null in a closed month. */
    public record Sums(Map<UUID, BigDecimal> spent, Map<UUID, BigDecimal> committed) {
    }

    private final RedisTemplate<String, Object> redisTemplate;

    public BudgetBalanceCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Sums get(UUID userId, YearMonth month, Supplier<Sums> loader) {
        String key = KEY_PREFIX + userId;
        String field = month.toString();
        try {
            if (redisTemplate.opsForHash().get(key, field) instanceof Map<?, ?> cached) {
                return fromCache(cached);
            }
        } catch (RuntimeException e) {
            log.warn("Budget balance cache unavailable, reading from the database: {}", e.getMessage());
            return loader.get();
        }
        Sums sums = loader.get();
        try {
            redisTemplate.opsForHash().put(key, field, toCache(sums));
            Long ttl = redisTemplate.getExpire(key);
            if (ttl == null || ttl < 0) {
                redisTemplate.expire(key, TTL);
            }
        } catch (RuntimeException e) {
            log.warn("Could not cache the budget balance: {}", e.getMessage());
        }
        return sums;
    }

    /** Clears every month of the user after the commit (right away without a transaction). */
    public void evict(UUID userId) {
        SubscriptionService.afterCommit(() -> {
            try {
                redisTemplate.delete(KEY_PREFIX + userId);
            } catch (RuntimeException e) {
                log.warn("Could not evict the budget balance of user {}: {}", userId, e.getMessage());
            }
        });
    }

    // amounts as strings: the generic Jackson serializer would bring numbers back as Double/Integer
    private static Map<String, Object> toCache(Sums sums) {
        Map<String, Object> value = new HashMap<>();
        value.put("spent", toStrings(sums.spent()));
        value.put("committed", sums.committed() == null ? null : toStrings(sums.committed()));
        return value;
    }

    private static Map<String, String> toStrings(Map<UUID, BigDecimal> sums) {
        Map<String, String> result = new HashMap<>();
        sums.forEach((id, amount) -> result.put(id.toString(), amount.toPlainString()));
        return result;
    }

    private static Sums fromCache(Map<?, ?> cached) {
        return new Sums(fromStrings((Map<?, ?>) cached.get("spent")),
            cached.get("committed") == null ? null : fromStrings((Map<?, ?>) cached.get("committed")));
    }

    private static Map<UUID, BigDecimal> fromStrings(Map<?, ?> sums) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        sums.forEach((id, amount) -> result.put(UUID.fromString((String) id), new BigDecimal((String) amount)));
        return result;
    }
}
