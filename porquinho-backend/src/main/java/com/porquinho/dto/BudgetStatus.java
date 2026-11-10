package com.porquinho.dto;

/** Status of a category in the month (Story 6.4), the same rule in the whole Epic 6. */
public enum BudgetStatus {
    /** remaining above 20% of the allocated */
    HEALTHY,
    /** 0 &le; remaining &le; 20% of the allocated */
    LOW,
    /** remaining below zero */
    OVERSPENT
}
