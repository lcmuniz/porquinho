package com.porquinho.dto;

import com.porquinho.entity.Goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Goal as returned by /api/v1/goals (Story 3.8). Money is a JSON number; the target date is ISO (yyyy-MM-dd).
 */
public record GoalResponse(
    UUID id,
    String name,
    String description,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate targetDate,
    BigDecimal monthlyAllocation,
    boolean completed,
    OffsetDateTime createdAt,
    String template,
    OffsetDateTime completedAt
) {
    public static GoalResponse of(Goal goal) {
        return new GoalResponse(
            goal.getId(),
            goal.getGoalName(),
            goal.getDescription(),
            goal.getTargetAmount(),
            goal.getCurrentAmount(),
            goal.getTargetDate(),
            goal.getMonthlyAllocation(),
            goal.isCompleted(),
            goal.getCreatedAt(),
            goal.getTemplate(),
            goal.getCompletedAt()
        );
    }
}
