package com.porquinho.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable async processing and scheduling.
 * Uses virtual threads (Java 21) via spring.threads.virtual.enabled=true in application.yml.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
