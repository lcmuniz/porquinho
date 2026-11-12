package com.porquinho.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/dashboard/layer1 (Story 7.3): the 5 essential metrics of the current month. {@code categories} holds the
 * budgeted child categories, overspent first, then low, then healthy, by name inside each group. {@code mainGoal} is
 * null without an active goal; its {@code percent} is 0-100, rounded down. {@code wealthHistory} (Story 7.4) is the
 * sum of the account balances over the last 12 months, oldest first: the first point is the start of the period, then
 * one point per São Paulo day in which a balance changed (value at the end of that day), and the last point is today
 * with {@code totalBalance}. Empty when the user has no balance snapshot at all.
 */
public record DashboardLayer1Response(
    BigDecimal totalBalance,
    List<CategoryStatus> categories,
    MainGoal mainGoal,
    BigDecimal monthSpent,
    BigDecimal monthAllocated,
    BigDecimal projectedBalance,
    List<WealthPoint> wealthHistory
) {
    public record WealthPoint(LocalDate date, BigDecimal balance) {
    }

    public record CategoryStatus(UUID categoryId, String name, BudgetStatus status, BigDecimal remaining) {
    }

    public record MainGoal(String name, BigDecimal currentAmount, BigDecimal targetAmount, int percent) {
    }
}
