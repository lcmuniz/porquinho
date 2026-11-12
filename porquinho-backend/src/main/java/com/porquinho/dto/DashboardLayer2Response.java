package com.porquinho.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/dashboard/layer2 (Story 7.5): the tactical view of the current {@code month} ("YYYY-MM").
 * {@code categories} holds the child categories with spending or allocation in the month, biggest spending first; a null
 * {@code categoryId} is the uncategorized spending ("Não classificado"). {@code allocated} and {@code status} are null
 * without allocation. {@code trends} has one entry per category with spending in the last 12 months (current one
 * included, partial), each with the 12 months oldest first, months without spending worth 0.
 */
public record DashboardLayer2Response(
    String month,
    List<CategorySpending> categories,
    List<CategoryTrend> trends
) {
    public record CategorySpending(UUID categoryId, String name, String colorHex, BigDecimal spent,
                                   BigDecimal allocated, BudgetStatus status) {
    }

    public record CategoryTrend(UUID categoryId, String name, List<MonthSpent> months) {
    }

    public record MonthSpent(String month, BigDecimal spent) {
    }
}
