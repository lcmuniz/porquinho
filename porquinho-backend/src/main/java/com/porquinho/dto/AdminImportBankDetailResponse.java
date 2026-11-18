package com.porquinho.dto;

import com.porquinho.entity.TransactionImport;
import com.porquinho.service.ImportErrorType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Story 10.2: one bank in [from, to]: failures per error type, the most recent imports (status filter applied) and
 * the daily error rate of the last 30 days up to today, whatever the period. No user id, file name or amount.
 */
public record AdminImportBankDetailResponse(
    String bank,
    LocalDate from,
    LocalDate to,
    List<ErrorTypeCount> errorTypes,
    List<Item> recent,
    List<DayPoint> trend
) {

    public record ErrorTypeCount(ImportErrorType type, long count) {
    }

    /** {@code errorType} only on a FAILED import; {@code message} is always one of the fixed processor messages. */
    public record Item(OffsetDateTime createdAt, String bank, TransactionImport.FileType fileType,
                       TransactionImport.Status status, ImportErrorType errorType, String message) {
    }

    /** {@code errorRate} in %, null on a day without a finished import. */
    public record DayPoint(LocalDate date, long total, long failures, Double errorRate) {
    }
}
