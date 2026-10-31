package com.porquinho.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a UTC Clock so date-dependent services (e.g. SubscriptionService) can be tested
 * with Clock.fixed instead of sleeps.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
