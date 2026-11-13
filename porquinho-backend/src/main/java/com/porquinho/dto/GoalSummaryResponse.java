package com.porquinho.dto;

import java.math.BigDecimal;

/** GET /api/v1/goals/summary (Story 8.2): sum of the active accounts' balances, as the budget's totalAvailable. */
public record GoalSummaryResponse(BigDecimal totalAvailable) {
}
