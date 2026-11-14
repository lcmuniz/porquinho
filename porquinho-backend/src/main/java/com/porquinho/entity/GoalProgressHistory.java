package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Snapshot of a goal's accumulated amount (Story 8.8, V37): at creation, on each contribution (contributionAmount
 * set) and on day 1 of each month. Insert-only.
 */
@Entity
@Table(name = "goal_progress_history")
public class GoalProgressHistory {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount_snapshot", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amountSnapshot;

    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private LocalDate snapshotDate;

    @Column(name = "contribution_amount", updatable = false, precision = 15, scale = 2)
    private BigDecimal contributionAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public GoalProgressHistory() {
    }

    public GoalProgressHistory(UUID userId, UUID goalId, BigDecimal amountSnapshot, LocalDate snapshotDate,
                               BigDecimal contributionAmount) {
        this.userId = userId;
        this.goalId = goalId;
        this.amountSnapshot = amountSnapshot;
        this.snapshotDate = snapshotDate;
        this.contributionAmount = contributionAmount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getAmountSnapshot() { return amountSnapshot; }
    public void setAmountSnapshot(BigDecimal amountSnapshot) { this.amountSnapshot = amountSnapshot; }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }

    public BigDecimal getContributionAmount() { return contributionAmount; }
    public void setContributionAmount(BigDecimal contributionAmount) { this.contributionAmount = contributionAmount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
