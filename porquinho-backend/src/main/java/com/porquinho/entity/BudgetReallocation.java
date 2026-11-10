package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Money moved from one child category to another in a month (Story 6.5, V33). Audit row: never updated or removed. */
@Entity
@Table(name = "budget_reallocations")
public class BudgetReallocation {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "from_category_id", nullable = false, updatable = false)
    private UUID fromCategoryId;

    @Column(name = "to_category_id", nullable = false, updatable = false)
    private UUID toCategoryId;

    @Column(name = "month_year", nullable = false, updatable = false)
    private LocalDate monthYear;

    @Column(name = "amount", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public BudgetReallocation() {
    }

    public BudgetReallocation(UUID userId, UUID fromCategoryId, UUID toCategoryId, LocalDate monthYear,
                              BigDecimal amount) {
        this.userId = userId;
        this.fromCategoryId = fromCategoryId;
        this.toCategoryId = toCategoryId;
        this.monthYear = monthYear;
        this.amount = amount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getFromCategoryId() { return fromCategoryId; }
    public void setFromCategoryId(UUID fromCategoryId) { this.fromCategoryId = fromCategoryId; }

    public UUID getToCategoryId() { return toCategoryId; }
    public void setToCategoryId(UUID toCategoryId) { this.toCategoryId = toCategoryId; }

    public LocalDate getMonthYear() { return monthYear; }
    public void setMonthYear(LocalDate monthYear) { this.monthYear = monthYear; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
