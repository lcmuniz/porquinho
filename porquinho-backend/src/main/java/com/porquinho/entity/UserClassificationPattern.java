package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The category the user chose for a normalized description (Story 5.11, V30). Written only by the native upsert of
 * {@link com.porquinho.repository.UserClassificationPatternRepository}; read to classify without the AI and to export.
 */
@Entity
@Table(name = "user_classification_patterns")
public class UserClassificationPattern {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "description_pattern", nullable = false, updatable = false, length = 500)
    private String descriptionPattern;

    @Column(name = "preferred_category_id", nullable = false)
    private UUID preferredCategoryId;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Column(name = "last_used_at", nullable = false)
    private OffsetDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UserClassificationPattern() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getDescriptionPattern() { return descriptionPattern; }
    public void setDescriptionPattern(String descriptionPattern) { this.descriptionPattern = descriptionPattern; }

    public UUID getPreferredCategoryId() { return preferredCategoryId; }
    public void setPreferredCategoryId(UUID preferredCategoryId) { this.preferredCategoryId = preferredCategoryId; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    public OffsetDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(OffsetDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
