package com.porquinho.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void shouldAllowFirstRequest() {
        // Arrange
        String key = "login:test@example.com";
        when(redisTemplate.execute(RateLimitService.INCREMENT_WITH_TTL, List.of("rate_limit:" + key), "60000")).thenReturn(1L);

        // Act
        boolean allowed = rateLimitService.allowRequest(key, 5, 60);

        // Assert
        assertTrue(allowed);
    }

    @Test
    void shouldAllow5Requests() {
        // Arrange
        String key = "login:test@example.com";

        // Simulate 5 requests
        for (int i = 1; i <= 5; i++) {
            when(redisTemplate.execute(RateLimitService.INCREMENT_WITH_TTL, List.of("rate_limit:" + key), "60000")).thenReturn((long) i);
            boolean allowed = rateLimitService.allowRequest(key, 5, 60);
            assertTrue(allowed, "Request " + i + " should be allowed");
        }
    }

    @Test
    void shouldBlock6thRequest() {
        // Arrange
        String key = "login:test@example.com";
        when(redisTemplate.execute(RateLimitService.INCREMENT_WITH_TTL, List.of("rate_limit:" + key), "60000")).thenReturn(6L);

        // Act
        boolean allowed = rateLimitService.allowRequest(key, 5, 60);

        // Assert
        assertFalse(allowed);
    }

    @Test
    void resetDeletesTheKey() {
        rateLimitService.reset("data_export:u1");

        verify(redisTemplate).delete("rate_limit:data_export:u1");
    }
}
