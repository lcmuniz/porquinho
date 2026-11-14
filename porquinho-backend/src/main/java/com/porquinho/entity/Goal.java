package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Financial goal (Story 3.8: the emergency fund created in onboarding; extended by Epic 8). Soft delete via
 * deleted_at. No @Where on purpose: the LGPD purge hard-deletes and the export includes removed rows; the
 * queries for the user's active goals filter deleted_at themselves (GoalRepository).
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "goal_name", nullable = false, length = 100)
    private String goalName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "monthly_allocation", precision = 15, scale = 2)
    private BigDecimal monthlyAllocation;

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "template", length = 30)
    private String template;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public Goal() {
    }

    public Goal(UUID userId, String goalName, BigDecimal targetAmount, LocalDate targetDate,
                BigDecimal monthlyAllocation) {
        this.userId = userId;
        this.goalName = goalName;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.monthlyAllocation = monthlyAllocation;
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

    public boolean isDeleted() { return deletedAt != null; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }

    public BigDecimal getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(BigDecimal currentAmount) { this.currentAmount = currentAmount; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public BigDecimal getMonthlyAllocation() { return monthlyAllocation; }
    public void setMonthlyAllocation(BigDecimal monthlyAllocation) { this.monthlyAllocation = monthlyAllocation; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    /**
     * Story 8.4 rule, by calendar month (same as goalPace in the front): ceil(missing / monthly allocation) months
     * from the current month past the target date's month.
     */
    public boolean isBehind(LocalDate today) {
        if (targetDate == null || monthlyAllocation == null || monthlyAllocation.signum() <= 0) return false;
        BigDecimal missing = targetAmount.subtract(currentAmount);
        if (missing.signum() <= 0) return false;
        BigDecimal months = missing.divide(monthlyAllocation, 0, RoundingMode.CEILING);
        long monthsLeft = YearMonth.from(today).until(YearMonth.from(targetDate), ChronoUnit.MONTHS);
        return months.compareTo(BigDecimal.valueOf(monthsLeft)) > 0;
    }
}
