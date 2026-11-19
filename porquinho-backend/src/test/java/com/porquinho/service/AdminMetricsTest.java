package com.porquinho.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMetricsTest {

    @Test
    void percentIsExactDecimalHalfUpAndNullWithoutWhole() {
        assertThat(AdminMetrics.percent(31, 80)).isEqualTo(38.8); // 38.75; a double would give 38.7
        assertThat(AdminMetrics.percent(1004, 10_000)).isEqualTo(10.0);
        assertThat(AdminMetrics.percent(1005, 10_000)).isEqualTo(10.1);
        assertThat(AdminMetrics.percent(0, 0)).isNull();
    }

    @Test
    void round1IsHalfUpOnTheDecimalTextAndKeepsNull() {
        assertThat(AdminMetrics.round1(98.95)).isEqualTo(99.0);
        assertThat(AdminMetrics.round1(98.94)).isEqualTo(98.9);
        assertThat(AdminMetrics.round1(-0.05)).isEqualTo(-0.1);
        assertThat(AdminMetrics.round1(null)).isNull();
    }

    @Test
    void startIsMidnightInSaoPaulo() {
        assertThat(AdminMetrics.start(LocalDate.of(2026, 11, 19)))
            .isEqualTo(OffsetDateTime.parse("2026-11-19T00:00:00-03:00"));
    }
}
