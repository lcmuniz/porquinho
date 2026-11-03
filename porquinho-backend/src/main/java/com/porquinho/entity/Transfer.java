package com.porquinho.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transfer between two of the user's own accounts (Story 4.5, V22). Written by TransferService together with
 * both balance changes and their "transfer" snapshots. No edit or removal yet; deleted_at is reserved for it.
 * The id comes from the client (idempotency key), so the entity is {@link Persistable}: a new instance is always
 * inserted (persist), never merged over an existing row with the same id.
 */
@Entity
@Table(name = "transfers")
public class Transfer implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** True until the row is inserted or loaded; not a column. */
    @Transient
    private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "from_account_id", nullable = false, updatable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false, updatable = false)
    private UUID toAccountId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transfer_date", nullable = false, updatable = false)
    private LocalDate transferDate;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Transfer() {
    }

    public Transfer(UUID id, UUID userId, UUID fromAccountId, UUID toAccountId, BigDecimal amount, LocalDate transferDate,
                    String description) {
        this.id = id;
        this.userId = userId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.transferDate = transferDate;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        newEntity = false;
    }

    @Override
    public boolean isNew() { return newEntity; }

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(UUID fromAccountId) { this.fromAccountId = fromAccountId; }

    public UUID getToAccountId() { return toAccountId; }
    public void setToAccountId(UUID toAccountId) { this.toAccountId = toAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
