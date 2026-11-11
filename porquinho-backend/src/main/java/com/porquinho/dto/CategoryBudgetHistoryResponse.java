package com.porquinho.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/budgets/categories/{categoryId}/history (Story 6.8). One entry per month with an allocation, up to the
 * current one, oldest first; {@code month} is YYYY-MM.
 */
public record CategoryBudgetHistoryResponse(UUID categoryId, String categoryName, List<Month> months) {

    public record Month(String month, BigDecimal allocated, BigDecimal spent, BigDecimal remaining,
                        BudgetStatus status) {
    }
}
