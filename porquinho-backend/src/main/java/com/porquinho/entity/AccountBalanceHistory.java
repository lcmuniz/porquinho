package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One snapshot of an account's current balance (Story 4.3, V20). Append-only: written on creation
 * ("initial"), when an edit changes the balance ("edit"), on both sides of a transfer ("transfer", Story 4.5) and
 * when a reconciliation changes the balance ("reconciliation", Story 4.6) and when a finished import changes it
 * ("transaction", Story 5.10). Read by the balance chart of Story 4.7. Written only by BalanceHistoryRecorder.
 */
@Entity
@Table(name = "account_balance_history")
public class AccountBalanceHistory {

    public static final String EVENT_INITIAL = "initial";
    public static final String EVENT_EDIT = "edit";
    public static final String EVENT_TRANSFER = "transfer";
    public static final String EVENT_RECONCILIATION = "reconciliation";
    public static final String EVENT_TRANSACTION = "transaction";

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "balance_snapshot", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal balanceSnapshot;

    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private OffsetDateTime snapshotDate;

    @Column(name = "created_by_event", nullable = false, updatable = false, length = 20)
    private String createdByEvent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** The transfer that produced a "transfer" snapshot (Story 4.5, V22); null for the other events. */
    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    public AccountBalanceHistory() {
    }

    public AccountBalanceHistory(UUID accountId, UUID userId, BigDecimal balanceSnapshot, String createdByEvent) {
        this.accountId = accountId;
        this.userId = userId;
        this.balanceSnapshot = balanceSnapshot;
        this.createdByEvent = createdByEvent;
    }

    /** snapshot_date is not defaulted here: BalanceHistoryRecorder is its only source (retro Epic 4, X5). */
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getBalanceSnapshot() { return balanceSnapshot; }
    public void setBalanceSnapshot(BigDecimal balanceSnapshot) { this.balanceSnapshot = balanceSnapshot; }

    public OffsetDateTime getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(OffsetDateTime snapshotDate) { this.snapshotDate = snapshotDate; }

    public String getCreatedByEvent() { return createdByEvent; }
    public void setCreatedByEvent(String createdByEvent) { this.createdByEvent = createdByEvent; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }
}
