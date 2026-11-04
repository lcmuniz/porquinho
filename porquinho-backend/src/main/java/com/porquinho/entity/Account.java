package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bank account (Story 3.2; extended by Epic 4). Soft delete via deleted_at.
 * No @Where on purpose: the LGPD purge hard-deletes and the export includes removed rows;
 * the queries for the user's active accounts filter deleted_at themselves (AccountRepository).
 */
@Entity
@Table(name = "accounts")
public class Account {

    public enum AccountType { CHECKING, SAVINGS }

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "initial_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal initialBalance;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** Who removed the row (Story 4.4, V21); cleared together with deleted_at on restore. */
    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** Last reconciliation with the bank (Story 4.6, V23); null if never reconciled. */
    @Column(name = "last_reconciliation_date")
    private OffsetDateTime lastReconciliationDate;

    /** Optimistic version (V24): every change of the row increments it; the edit sends it back (409 if stale). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Account() {
    }

    /** New account: the current balance starts equal to the initial balance. */
    public Account(UUID userId, String accountName, String bankName, AccountType accountType, BigDecimal initialBalance) {
        this.userId = userId;
        this.accountName = accountName;
        this.bankName = bankName;
        this.accountType = accountType;
        this.initialBalance = initialBalance;
        this.currentBalance = initialBalance;
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

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }

    public OffsetDateTime getLastReconciliationDate() { return lastReconciliationDate; }
    public void setLastReconciliationDate(OffsetDateTime lastReconciliationDate) {
        this.lastReconciliationDate = lastReconciliationDate;
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
