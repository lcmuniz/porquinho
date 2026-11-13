package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * How much the user allocated to and contributed to a goal in a month (Story 8.2, V34). month_year is always day 1.
 * One row per goal and month.
 */
@Entity
@Table(name = "goal_allocations")
public class GoalAllocation {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "month_year", nullable = false, updatable = false)
    private LocalDate monthYear;

    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(name = "contributed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal contributedAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public GoalAllocation() {
    }

    public GoalAllocation(UUID userId, UUID goalId, LocalDate monthYear, BigDecimal allocatedAmount) {
        this.userId = userId;
        this.goalId = goalId;
        this.monthYear = monthYear;
        this.allocatedAmount = allocatedAmount;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }

    public LocalDate getMonthYear() { return monthYear; }
    public void setMonthYear(LocalDate monthYear) { this.monthYear = monthYear; }

    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }

    public BigDecimal getContributedAmount() { return contributedAmount; }
    public void setContributedAmount(BigDecimal contributedAmount) { this.contributedAmount = contributedAmount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
