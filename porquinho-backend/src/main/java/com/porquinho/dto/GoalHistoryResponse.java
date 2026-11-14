package com.porquinho.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/v1/goals/{id}/history (Story 8.8): the goal's snapshots, oldest first. contributionAmount is null for the
 * creation and monthly snapshots; amount is the accumulated amount after the entry.
 */
public record GoalHistoryResponse(String goalName, BigDecimal targetAmount, OffsetDateTime createdAt,
                                  OffsetDateTime completedAt, List<Entry> entries) {

    public record Entry(LocalDate date, BigDecimal contributionAmount, BigDecimal amount) {
    }
}
