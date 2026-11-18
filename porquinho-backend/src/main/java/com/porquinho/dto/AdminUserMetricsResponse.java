package com.porquinho.dto;

import com.porquinho.dto.AdminHealthResponse.Status;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Story 10.4: users of the admin panel, "now" (no period). Aggregates only, never a user id, e-mail or name.
 * Percentages with 1 decimal, null = "sem dados" (no denominator).
 */
public record AdminUserMetricsResponse(
    OffsetDateTime generatedAt,
    long total,
    long active24h,
    long active7d,
    long active30d,
    long trials,
    long paid,
    /** MONTHLY CANCELLED, EXPIRED or SUSPENDED over every MONTHLY; GREEN up to the target, RED above. */
    Goal churn,
    /** Weeks with a completed import per user active in 30 days, average of the last 4 complete weeks (2 decimals). */
    Double weeklyCheckins,
    /** Completed onboarding over the base; GREEN from the target, YELLOW below. */
    Goal onboarding,
    /** MONTHLY over MONTHLY plus the trials already ended. */
    Double conversion,
    List<Feature> adoption,
    /** YYYY-MM, the last 12 months up to the current one. */
    List<String> months,
    /** Users of the base at the end of each month of {@code months}. */
    List<Long> growth,
    /** Conversion of the users created in each month of {@code months}; null without a denominator. */
    List<Double> conversionByMonth,
    /** YYYY-MM-DD (Monday), the last 12 weeks up to the current one. */
    List<String> weeks,
    /** Active in 7 days at the end of each week of {@code weeks} (the current one: now). */
    List<Long> activeWeekly,
    /** Active in 30 days at the end of each week of {@code weeks}. */
    List<Long> activeMonthly
) {

    public record Goal(Double rate, double target, Status status) {
    }

    /** {@code feature}: ACCOUNTS, CREDIT_CARDS, IMPORTS, BUDGETS, GOALS, TRANSFERS or RECONCILIATIONS. */
    public record Feature(String feature, long users, Double rate) {
    }
}
