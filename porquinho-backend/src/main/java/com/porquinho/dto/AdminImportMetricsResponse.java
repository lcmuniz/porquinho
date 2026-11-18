package com.porquinho.dto;

import com.porquinho.dto.AdminHealthResponse.Status;

import java.time.LocalDate;
import java.util.List;

/**
 * Story 10.2: import error rate per bank in [from, to] (days in America/Sao_Paulo). Aggregates only. {@code bank} is
 * the detected code, UNKNOWN when the import failed before the detection. {@code errorRate} in % is null without a
 * finished import (PROCESSING is not counted); RED above {@code target}, GREEN up to it, UNKNOWN without data.
 */
public record AdminImportMetricsResponse(LocalDate from, LocalDate to, double target, List<Bank> banks) {

    public record Bank(String bank, long total, long successes, long failures, Double errorRate, Status status) {
    }
}
