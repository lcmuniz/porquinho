package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The atomic INCR + PEXPIRE script against the real Redis: the first hit sets the TTL, later hits keep it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class RedisCounterIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void firstIncrementSetsTheTtlAndLaterOnesDoNotExtendIt() {
        String key = "test_counter:" + UUID.randomUUID();
        try {
            assertThat(RateLimitService.increment(redis, key, Duration.ofSeconds(60))).isEqualTo(1L);
            redis.expire(key, Duration.ofSeconds(30));
            assertThat(RateLimitService.increment(redis, key, Duration.ofSeconds(60))).isEqualTo(2L);

            assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 30L);
        } finally {
            redis.delete(key);
        }
    }
}
