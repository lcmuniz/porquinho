package com.porquinho.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A budget alert e-mail already sent (pre-Epic 10, V46): at most one per user, category, month (day 1) and level
 * (90 or 100). Written only through {@code BudgetAlertNotificationRepository.insertIfAbsent}; read for the LGPD export.
 */
@Entity
@Table(name = "budget_alert_notifications", uniqueConstraints = @UniqueConstraint(
    name = "uk_budget_alert_notifications", columnNames = {"user_id", "category_id", "month", "level"}))
public class BudgetAlertNotification {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "month", nullable = false, updatable = false)
    private LocalDate month;

    @Column(name = "level", nullable = false, updatable = false)
    private short level;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    public BudgetAlertNotification() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public LocalDate getMonth() { return month; }
    public void setMonth(LocalDate month) { this.month = month; }

    public short getLevel() { return level; }
    public void setLevel(short level) { this.level = level; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }
}
