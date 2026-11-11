package com.porquinho.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Temporary lock after repeated failed logins.
 *
 * The failure signal comes from a public endpoint (Supabase does the actual authentication), so anyone can send it.
 * To keep that signal from being used to lock out a victim, the counter is kept per (email, client IP): only
 * the IP that keeps failing gets locked out, and other users of the same account keep logging in normally.
 * The counter lives in Redis and expires on its own after the lock window.
 */
@Service
public class AccountLockService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public AccountLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Records a failed login from this IP. After 5 failures within 15 minutes, this IP is locked for this email.
     */
    public void recordFailedAttempt(String email, String clientIp) {
        String key = key(email, clientIp);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCK_DURATION);
        }
    }

    /**
     * @return true if this IP has 5 or more recent failures for this email
     */
    public boolean isLocked(String email, String clientIp) {
        String value = redisTemplate.opsForValue().get(key(email, clientIp));
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= MAX_FAILED_ATTEMPTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Clears the failure counter for this IP after a successful login.
     */
    public void resetFailedAttempts(String email, String clientIp) {
        redisTemplate.delete(key(email, clientIp));
    }

    static String key(String email, String clientIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "login_failed:" + normalizedEmail + ":" + (clientIp == null ? "unknown" : clientIp);
    }
}
