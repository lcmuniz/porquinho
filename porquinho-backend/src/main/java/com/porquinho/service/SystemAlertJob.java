package com.porquinho.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Story 10.5: runs the critical metrics check every 5 minutes. Off with {@code app.system-alerts.enabled=false} (the
 * tests, like {@link HealthSnapshotJob}), so the cron never opens alerts under a test; they call
 * {@link SystemAlertService#runAlerts()} directly.
 */
@Component
@ConditionalOnProperty(name = "app.system-alerts.enabled", havingValue = "true", matchIfMissing = true)
public class SystemAlertJob {

    private final SystemAlertService systemAlertService;

    public SystemAlertJob(SystemAlertService systemAlertService) {
        this.systemAlertService = systemAlertService;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "America/Sao_Paulo")
    public void run() {
        systemAlertService.runAlerts();
    }
}
