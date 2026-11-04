package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One reconciliation of an account's balance with the bank (Story 4.6, V23). Append-only: written by
 * ReconciliationService together with the new current balance. Decision A: there is no adjustment transaction;
 * this row (previous balance, bank balance, difference, reason, date) is the record of the adjustment.
 */
@Entity
@Table(name = "reconciliation_history")
public class Reconciliation {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "previous_balance", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal previousBalance;

    @Column(name = "bank_balance", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal bankBalance;

    @Column(name = "difference", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal difference;

    @Column(name = "reason", updatable = false, length = 200)
    private String reason;

    @Column(name = "reconciled_at", nullable = false, updatable = false)
    private OffsetDateTime reconciledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Reconciliation() {
    }

    public Reconciliation(UUID accountId, UUID userId, BigDecimal previousBalance, BigDecimal bankBalance,
                          BigDecimal difference, String reason, OffsetDateTime reconciledAt) {
        this.accountId = accountId;
        this.userId = userId;
        this.previousBalance = previousBalance;
        this.bankBalance = bankBalance;
        this.difference = difference;
        this.reason = reason;
        this.reconciledAt = reconciledAt;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (reconciledAt == null) {
            reconciledAt = now;
        }
        createdAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getPreviousBalance() { return previousBalance; }
    public void setPreviousBalance(BigDecimal previousBalance) { this.previousBalance = previousBalance; }

    public BigDecimal getBankBalance() { return bankBalance; }
    public void setBankBalance(BigDecimal bankBalance) { this.bankBalance = bankBalance; }

    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public OffsetDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(OffsetDateTime reconciledAt) { this.reconciledAt = reconciledAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
