package com.porquinho.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Service for rate limiting using Redis.
 * Implements sliding window rate limiting to prevent abuse.
 */
@Service
public class RateLimitService {

    static final String KEY_PREFIX = "rate_limit:";

    /** INCR and, on the first hit, PEXPIRE in one atomic step: a failure between them can't leave a key that never expires. */
    static final RedisScript<Long> INCREMENT_WITH_TTL = RedisScript.of(
        "local count = redis.call('INCR', KEYS[1]) "
            + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
            + "return count", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if a request is allowed based on rate limiting.
     * Uses Redis sliding window to track request counts.
     *
     * @param key Unique key for the rate limit (e.g., "login:email@example.com")
     * @param maxAttempts Maximum number of attempts allowed in the window
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String key, int maxAttempts, int windowSeconds) {
        String redisKey = KEY_PREFIX + key;

        // Increment counter in Redis; the window starts on the first request
        Long count = increment(redisTemplate, redisKey, Duration.ofSeconds(windowSeconds));

        // Check if count exceeds max attempts
        return count <= maxAttempts;
    }

    /** Atomic counter that expires {@code ttl} after its first increment. */
    static Long increment(StringRedisTemplate redisTemplate, String key, Duration ttl) {
        return redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(ttl.toMillis()));
    }

    /**
     * Story 9.3: frees the slot again (e.g. a data export that ended FAILED can be retried at once).
     * Deletes the whole window, so use it only for limit-1 keys: on a key like {@code login:} it would
     * also wipe the failed attempts that the brute-force protection counts.
     */
    public void reset(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}
