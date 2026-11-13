package com.porquinho.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/goals/{id}/progress (Story 8.3): accumulated amount per month (yyyy-MM), oldest first. */
public record GoalProgressResponse(List<Point> points) {

    public record Point(String month, BigDecimal amount) {
    }
}
