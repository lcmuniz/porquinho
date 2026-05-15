package com.porquinho.dto;

import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for account deletion requests (Story 1.7).
 */
public class AccountDeletionResponse {

    private UUID id;
    private DeletionStatus status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime scheduledPurgeAt;

    public AccountDeletionResponse() {
    }

    public AccountDeletionResponse(UUID id, DeletionStatus status,
                                   OffsetDateTime requestedAt,
                                   OffsetDateTime scheduledPurgeAt) {
        this.id = id;
        this.status = status;
        this.requestedAt = requestedAt;
        this.scheduledPurgeAt = scheduledPurgeAt;
    }

    public static AccountDeletionResponse fromEntity(AccountDeletion deletion) {
        return new AccountDeletionResponse(
            deletion.getId(),
            deletion.getStatus(),
            deletion.getRequestedAt(),
            deletion.getScheduledPurgeAt()
        );
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public DeletionStatus getStatus() { return status; }
    public void setStatus(DeletionStatus status) { this.status = status; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public OffsetDateTime getScheduledPurgeAt() { return scheduledPurgeAt; }
    public void setScheduledPurgeAt(OffsetDateTime scheduledPurgeAt) { this.scheduledPurgeAt = scheduledPurgeAt; }
}
