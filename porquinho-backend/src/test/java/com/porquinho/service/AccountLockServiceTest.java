package com.porquinho.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AccountLockService: lock per (email, client IP), kept in Redis.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountLockServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String IP = "203.0.113.7";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AccountLockService accountLockService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        accountLockService = new AccountLockService(redisTemplate);
    }

    @Test
    void failureIncrementsTheCounterWithTheLockWindowAtomically() {
        accountLockService.recordFailedAttempt(EMAIL, IP);

        verify(redisTemplate).execute(RateLimitService.INCREMENT_WITH_TTL, List.of(AccountLockService.key(EMAIL, IP)),
            String.valueOf(AccountLockService.LOCK_DURATION.toMillis()));
    }

    @Test
    void lockedAfterFiveFailuresFromTheSameIp() {
        when(valueOperations.get(AccountLockService.key(EMAIL, IP))).thenReturn("5");

        assertTrue(accountLockService.isLocked(EMAIL, IP));
    }

    @Test
    void notLockedBelowFiveFailures() {
        when(valueOperations.get(AccountLockService.key(EMAIL, IP))).thenReturn("4");

        assertFalse(accountLockService.isLocked(EMAIL, IP));
    }

    @Test
    void notLockedWithoutFailures() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertFalse(accountLockService.isLocked(EMAIL, IP));
    }

    @Test
    void failuresFromAnotherIpDoNotLockTheVictim() {
        String attackerIp = "198.51.100.9";
        when(valueOperations.get(AccountLockService.key(EMAIL, attackerIp))).thenReturn("50");
        when(valueOperations.get(AccountLockService.key(EMAIL, IP))).thenReturn(null);

        assertTrue(accountLockService.isLocked(EMAIL, attackerIp));
        assertFalse(accountLockService.isLocked(EMAIL, IP));
        assertNotEquals(AccountLockService.key(EMAIL, attackerIp), AccountLockService.key(EMAIL, IP));
    }

    @Test
    void emailIsNormalizedInTheKey() {
        assertEquals(AccountLockService.key(EMAIL, IP), AccountLockService.key("  Test@Example.COM ", IP));
    }

    @Test
    void resetClearsTheCounterForThisIp() {
        accountLockService.resetFailedAttempts(EMAIL, IP);

        verify(redisTemplate).delete(AccountLockService.key(EMAIL, IP));
    }
}
