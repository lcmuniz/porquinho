package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Spending category / budget envelope (Story 3.4; extended by Epics 5 and 6). One level of hierarchy via
 * parent_category_id (the suggested groups are parents). Soft delete via deleted_at.
 * No @Where on purpose: the LGPD purge hard-deletes and the export includes removed rows;
 * the queries for the user's active categories filter deleted_at themselves (CategoryRepository).
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "icon_name", nullable = false, length = 50)
    private String iconName;

    @Column(name = "is_system_default", nullable = false)
    private boolean systemDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Category() {
    }

    public Category(UUID userId, String categoryName, UUID parentCategoryId, String colorHex, String iconName,
                    boolean systemDefault) {
        this.userId = userId;
        this.categoryName = categoryName;
        this.parentCategoryId = parentCategoryId;
        this.colorHex = colorHex;
        this.iconName = iconName;
        this.systemDefault = systemDefault;
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

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public UUID getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(UUID parentCategoryId) { this.parentCategoryId = parentCategoryId; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public boolean isSystemDefault() { return systemDefault; }
    public void setSystemDefault(boolean systemDefault) { this.systemDefault = systemDefault; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
