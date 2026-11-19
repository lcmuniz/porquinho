package com.porquinho.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static com.porquinho.service.ImportMetricsService.SAO_PAULO;

/**
 * Retro do Epic 10, A2: the one rounding rule of the admin panel (10.1-10.4) and the 10.5 alerts. A metric is rounded
 * once to 1 decimal (HALF_UP) before its color, its alert and its display, so the panel and the alert never disagree.
 */
final class AdminMetrics {

    private AdminMetrics() {
    }

    /** part / whole in percent, exact decimal (38.75 gives 38.8; a double would give 38.7); null when whole is 0. */
    static Double percent(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** A measured value (uptime, milliseconds, a difference) to 1 decimal; null stays null. */
    static Double round1(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Start of the day in America/Sao_Paulo, the day of every period filter of the panel. */
    static OffsetDateTime start(LocalDate day) {
        return day.atStartOfDay(SAO_PAULO).toOffsetDateTime();
    }
}
