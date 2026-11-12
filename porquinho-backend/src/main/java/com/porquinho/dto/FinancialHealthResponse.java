package com.porquinho.dto;

/**
 * GET /api/v1/dashboard/health (Story 7.2): the dashboard's traffic light and the counts behind it. Categories are the
 * current month's child categories with a budget; accounts are the active ones (cards left out).
 */
public record FinancialHealthResponse(
    HealthStatus status,
    int healthyCategories,
    int lowCategories,
    int overspentCategories,
    int negativeAccounts,
    int goalsBehind
) {
    public enum HealthStatus { HEALTHY, ATTENTION, CRITICAL }
}
