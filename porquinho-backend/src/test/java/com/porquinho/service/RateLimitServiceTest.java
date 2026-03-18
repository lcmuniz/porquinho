package com.porquinho.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RateLimitService.
 * Tests sliding window rate limiting with Redis.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void shouldAllowFirstRequest() {
        // Arrange
        String key = "login:test@example.com";
        when(valueOperations.increment("rate_limit:" + key)).thenReturn(1L);

        // Act
        boolean allowed = rateLimitService.allowRequest(key, 5, 60);

        // Assert
        assertTrue(allowed);
        verify(redisTemplate).expire(eq("rate_limit:" + key), eq(Duration.ofSeconds(60)));
    }

    @Test
    void shouldAllow5Requests() {
        // Arrange
        String key = "login:test@example.com";

        // Simulate 5 requests
        for (int i = 1; i <= 5; i++) {
            when(valueOperations.increment("rate_limit:" + key)).thenReturn((long) i);
            boolean allowed = rateLimitService.allowRequest(key, 5, 60);
            assertTrue(allowed, "Request " + i + " should be allowed");
        }
    }

    @Test
    void shouldBlock6thRequest() {
        // Arrange
        String key = "login:test@example.com";
        when(valueOperations.increment("rate_limit:" + key)).thenReturn(6L);

        // Act
        boolean allowed = rateLimitService.allowRequest(key, 5, 60);

        // Assert
        assertFalse(allowed);
    }
}
