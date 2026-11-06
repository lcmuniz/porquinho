package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What the AI answered for one transaction (Story 5.8, V28). A null {@link #predictedCategoryId} means the AI answered
 * that no category fits. The correction fields are filled when the user changes the category (Story 5.10).
 */
@Entity
@Table(name = "ai_classification_history")
public class AiClassificationHistory {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "predicted_category_id", updatable = false)
    private UUID predictedCategoryId;

    @Column(name = "confidence", nullable = false, updatable = false)
    private int confidence;

    @Column(name = "was_corrected", nullable = false)
    private boolean wasCorrected;

    @Column(name = "corrected_category_id")
    private UUID correctedCategoryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AiClassificationHistory() {
    }

    public AiClassificationHistory(UUID userId, UUID transactionId, UUID predictedCategoryId, int confidence) {
        this.userId = userId;
        this.transactionId = transactionId;
        this.predictedCategoryId = predictedCategoryId;
        this.confidence = confidence;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public UUID getPredictedCategoryId() { return predictedCategoryId; }
    public void setPredictedCategoryId(UUID predictedCategoryId) { this.predictedCategoryId = predictedCategoryId; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public boolean isWasCorrected() { return wasCorrected; }
    public void setWasCorrected(boolean wasCorrected) { this.wasCorrected = wasCorrected; }

    public UUID getCorrectedCategoryId() { return correctedCategoryId; }
    public void setCorrectedCategoryId(UUID correctedCategoryId) { this.correctedCategoryId = correctedCategoryId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
