package com.porquinho.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GET/PUT /api/v1/budgets/{YYYY-MM} (Story 6.3). One entry per active child category, plus one per removed child
 * that still has an allocation in the month (a closed month keeps it as history, retro Epic 6 A4). {@code allocated} is null when
 * the month has no allocation for it; {@code committed} is null in a closed month (before the current one).
 * {@code remaining} (allocated - spent) and {@code status} (Story 6.4) are null without allocation.
 * {@code version} identifies the month's allocations; the PUT sends it back and gets 409 if it changed meanwhile.
 */
public record BudgetResponse(
    String month,
    String version,
    BigDecimal totalAvailable,
    BigDecimal totalAllocated,
    List<CategoryBudget> categories
) {
    /** {@code removedCategoryName}: null for an active child; the name of a removed one kept as history (A4). */
    public record CategoryBudget(UUID categoryId, BigDecimal allocated, BigDecimal spent, BigDecimal committed,
                                 BigDecimal remaining, BudgetStatus status, String removedCategoryName) {
        public CategoryBudget(UUID categoryId, BigDecimal allocated, BigDecimal spent, BigDecimal committed,
                              BigDecimal remaining, BudgetStatus status) {
            this(categoryId, allocated, spent, committed, remaining, status, null);
        }
    }
}
