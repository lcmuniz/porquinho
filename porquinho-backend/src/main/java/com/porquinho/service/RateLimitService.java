package com.porquinho.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for rate limiting using Redis.
 * Implements sliding window rate limiting to prevent abuse.
 */
@Service
public class RateLimitService {

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
        String redisKey = "rate_limit:" + key;

        // Increment counter in Redis
        Long count = redisTemplate.opsForValue().increment(redisKey);

        // Set expiration on first request
        if (count == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        // Check if count exceeds max attempts
        return count <= maxAttempts;
    }
}
