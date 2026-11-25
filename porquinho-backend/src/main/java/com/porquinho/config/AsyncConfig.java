package com.porquinho.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable async processing and scheduling.
 * Platform threads: spring.threads.virtual.enabled=false in application.yml, which says why.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
