package com.porquinho.dto;

import com.porquinho.dto.AdminHealthResponse.Status;

import java.time.LocalDate;
import java.util.List;

/**
 * Story 10.3: one bank in [from, to]: accuracy per transaction type (PIX, INSTALLMENT, DEBIT, CREDIT) and per AI
 * confidence band (0-49, 50-79, 80-100), the correction patterns shared by at least 3 users (every bank: patterns
 * have none) and the suggestions. No user id, category name, amount or other description.
 */
public record AdminAiBankDetailResponse(
    String bank,
    LocalDate from,
    LocalDate to,
    List<Group> types,
    List<Group> confidence,
    List<Pattern> patterns,
    List<String> suggestions
) {

    public record Group(String key, long classifications, long corrections, Double accuracy, Status status) {
    }

    public record Pattern(String pattern, long users, long usage) {
    }
}
