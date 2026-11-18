package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Story 10.1: the per-minute snapshot job is on by default and only off with app.health-snapshot.enabled=false. */
class HealthSnapshotJobConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(HealthSnapshotService.class, () -> mock(HealthSnapshotService.class))
        .withUserConfiguration(HealthSnapshotJob.class);

    @Test
    void onWithoutTheProperty() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(HealthSnapshotJob.class));
    }

    @Test
    void onWithTrue() {
        runner.withPropertyValues("app.health-snapshot.enabled=true")
            .run(ctx -> assertThat(ctx).hasSingleBean(HealthSnapshotJob.class));
    }

    @Test
    void offWithFalse() {
        runner.withPropertyValues("app.health-snapshot.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(HealthSnapshotJob.class));
    }
}
