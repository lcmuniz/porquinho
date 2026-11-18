package com.porquinho.dto;

import com.porquinho.dto.AdminHealthResponse.Status;

import java.time.LocalDate;
import java.util.List;

/**
 * Story 10.3: AI classification accuracy in [from, to] (days in America/Sao_Paulo), overall and per bank, plus the
 * monthly accuracy of the last 6 months per bank whatever the period. Aggregates only. {@code accuracy} in % is null
 * without a classification; GREEN from {@code target}, YELLOW from 75, RED below, UNKNOWN without data.
 */
public record AdminAiMetricsResponse(
    LocalDate from,
    LocalDate to,
    double target,
    Bank overall,
    MonthChange monthChange,
    List<Bank> banks,
    List<String> months,
    List<Series> trend
) {

    public record Bank(String bank, long classifications, long corrections, Double accuracy, Status status) {
    }

    /** Overall accuracy of the current and the previous month; {@code delta} in p.p., null when one has no data. */
    public record MonthChange(Double current, Double previous, Double delta) {
    }

    /** One point per entry of {@code months} (YYYY-MM, oldest first); a month without data has a null accuracy. */
    public record Series(String bank, List<Double> accuracy) {
    }
}
