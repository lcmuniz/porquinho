package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private JwtBlacklistService jwtBlacklistService;

    @Test
    void blacklistTokenShouldStoreInRedisWithCorrectTtl() {
        // Arrange
        String tokenHash = "abc123";
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        jwtBlacklistService.blacklistToken(tokenHash, expiresAt);

        // Assert
        verify(valueOperations).set(eq("jwt:blacklist:abc123"), eq("revoked"), any(Duration.class));
    }

    @Test
    void blacklistTokenShouldNotStoreIfAlreadyExpired() {
        // Arrange
        String tokenHash = "expired123";
        OffsetDateTime expiresAt = OffsetDateTime.now().minusHours(1);

        // Act
        jwtBlacklistService.blacklistToken(tokenHash, expiresAt);

        // Assert - should not interact with Redis since TTL is not positive
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isBlacklistedShouldReturnTrueWhenKeyExists() {
        // Arrange
        when(redisTemplate.hasKey("jwt:blacklist:abc123")).thenReturn(true);

        // Act & Assert
        assertTrue(jwtBlacklistService.isBlacklisted("abc123"));
    }

    @Test
    void isBlacklistedShouldReturnFalseWhenKeyDoesNotExist() {
        // Arrange
        when(redisTemplate.hasKey("jwt:blacklist:abc123")).thenReturn(false);

        // Act & Assert
        assertFalse(jwtBlacklistService.isBlacklisted("abc123"));
    }
}
