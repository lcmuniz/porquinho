package com.porquinho.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/dashboard/layer1 (Story 7.3): the 5 essential metrics of the current month. {@code categories} holds the
 * budgeted child categories, overspent first, then low, then healthy, by name inside each group. {@code mainGoal} is
 * null without an active goal; its {@code percent} is 0-100, rounded down.
 */
public record DashboardLayer1Response(
    BigDecimal totalBalance,
    List<CategoryStatus> categories,
    MainGoal mainGoal,
    BigDecimal monthSpent,
    BigDecimal monthAllocated,
    BigDecimal projectedBalance
) {
    public record CategoryStatus(UUID categoryId, String name, BudgetStatus status, BigDecimal remaining) {
    }

    public record MainGoal(String name, BigDecimal currentAmount, BigDecimal targetAmount, int percent) {
    }
}
