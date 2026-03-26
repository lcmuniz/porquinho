package com.porquinho.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public JwtBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklistToken(String tokenHash, OffsetDateTime expiresAt) {
        Duration ttl = Duration.between(OffsetDateTime.now(), expiresAt);
        if (ttl.isPositive()) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + tokenHash, "revoked", ttl);
        }
    }

    public boolean isBlacklisted(String tokenHash) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenHash));
    }
}
