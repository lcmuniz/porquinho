package com.porquinho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Story 7.7: the dashboard responses cached in Redis as JSON (Spring's ObjectMapper, so BigDecimal and dates come back
 * as they were). {@code dashboard:health|layer1|layer2:{userId}} are plain keys; {@code dashboard:layer3:{userId}} is a
 * hash with one field per filters hash, its TTL set only when the hash is created, so one DEL clears every page and
 * filter without SCAN (the 6.4 trick). Redis down never breaks a read or an evict: it only logs. Hits and misses go to
 * the {@code dashboard.cache{layer,result}} counter.
 */
@Component
public class DashboardCache {

    public enum Layer {
        HEALTH("health", Duration.ofSeconds(30)),
        LAYER1("layer1", Duration.ofSeconds(30)),
        LAYER2("layer2", Duration.ofMinutes(5)),
        LAYER3("layer3", Duration.ofMinutes(15));

        final String name;
        final Duration ttl;

        Layer(String name, Duration ttl) {
            this.name = name;
            this.ttl = ttl;
        }

        String key(UUID userId) {
            return "dashboard:" + name + ":" + userId;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(DashboardCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public DashboardCache(StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** {@code field} is the filters hash of the layer 3 (a hash field) and null for the other layers (a plain key). */
    public <T> T get(Layer layer, UUID userId, String field, Class<T> type, Supplier<T> loader) {
        String key = layer.key(userId);
        try {
            String cached = field == null ? redis.opsForValue().get(key) : (String) redis.opsForHash().get(key, field);
            if (cached != null) {
                T value = objectMapper.readValue(cached, type);
                count(layer, "hit");
                return value;
            }
        } catch (Exception e) {
            log.warn("Dashboard cache unavailable, reading from the database: {}", e.getMessage());
            // apart from the misses: an outage must not look like a cold cache in the hit rate
            count(layer, "error");
            return loader.get();
        }
        count(layer, "miss");
        // the loader runs outside the try: a 400 (bad page or date) propagates and nothing is stored
        T value = loader.get();
        try {
            String json = objectMapper.writeValueAsString(value);
            if (field == null) {
                redis.opsForValue().set(key, json, layer.ttl);
            } else {
                redis.opsForHash().put(key, field, json);
                Long ttl = redis.getExpire(key);
                if (ttl == null || ttl < 0) redis.expire(key, layer.ttl);
            }
        } catch (Exception e) {
            log.warn("Could not cache the dashboard {}: {}", layer.name, e.getMessage());
        }
        return value;
    }

    /** Every layer of the user, after the commit (right away without a transaction). */
    public void evictAll(UUID userId) {
        evict(userId, List.of(Layer.values()));
    }

    /** A budget change does not touch the transaction list: the layer 3 stays. */
    public void evictBudgetLayers(UUID userId) {
        evict(userId, List.of(Layer.HEALTH, Layer.LAYER1, Layer.LAYER2));
    }

    private void evict(UUID userId, List<Layer> layers) {
        SubscriptionService.afterCommit(() -> {
            try {
                redis.delete(layers.stream().map(l -> l.key(userId)).toList());
            } catch (RuntimeException e) {
                log.warn("Could not evict the dashboard cache of user {}: {}", userId, e.getMessage());
            }
        });
    }

    private void count(Layer layer, String result) {
        Counter.builder("dashboard.cache").tag("layer", layer.name).tag("result", result)
            .register(meterRegistry).increment();
    }
}
