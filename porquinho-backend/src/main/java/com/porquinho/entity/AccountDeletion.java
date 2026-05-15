package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AccountDeletion entity tracking LGPD account deletion requests (Story 1.7).
 *
 * Records persist after the hard-delete (status=PURGED) for audit/compliance.
 * No @Where clause: we want to query records even after purged_at is set.
 */
@Entity
@Table(name = "account_deletions")
public class AccountDeletion {

    public enum DeletionStatus {
        PENDING_PURGE, PURGED
    }

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeletionStatus status = DeletionStatus.PENDING_PURGE;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "scheduled_purge_at", nullable = false)
    private OffsetDateTime scheduledPurgeAt;

    @Column(name = "purged_at")
    private OffsetDateTime purgedAt;

    @Column(name = "requested_ip", length = 45)
    private String requestedIp;

    public AccountDeletion() {
    }

    public AccountDeletion(UUID userId, OffsetDateTime scheduledPurgeAt, String requestedIp) {
        this.userId = userId;
        this.scheduledPurgeAt = scheduledPurgeAt;
        this.requestedIp = requestedIp;
        this.status = DeletionStatus.PENDING_PURGE;
    }

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public DeletionStatus getStatus() { return status; }
    public void setStatus(DeletionStatus status) { this.status = status; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public OffsetDateTime getScheduledPurgeAt() { return scheduledPurgeAt; }
    public void setScheduledPurgeAt(OffsetDateTime scheduledPurgeAt) { this.scheduledPurgeAt = scheduledPurgeAt; }

    public OffsetDateTime getPurgedAt() { return purgedAt; }
    public void setPurgedAt(OffsetDateTime purgedAt) { this.purgedAt = purgedAt; }

    public String getRequestedIp() { return requestedIp; }
    public void setRequestedIp(String requestedIp) { this.requestedIp = requestedIp; }
}
