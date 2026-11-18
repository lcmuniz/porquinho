package com.porquinho.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Story 10.1: runs the per-minute health snapshot. Off with {@code app.health-snapshot.enabled=false} (the tests), so
 * the cron never writes rows under a test; the tests call {@link HealthSnapshotService#recordSnapshot()} directly.
 */
@Component
@ConditionalOnProperty(name = "app.health-snapshot.enabled", havingValue = "true", matchIfMissing = true)
public class HealthSnapshotJob {

    private final HealthSnapshotService healthSnapshotService;

    public HealthSnapshotJob(HealthSnapshotService healthSnapshotService) {
        this.healthSnapshotService = healthSnapshotService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void run() {
        healthSnapshotService.recordSnapshot();
    }
}
