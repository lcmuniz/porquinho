package com.porquinho.dto;

import com.porquinho.entity.Category;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Category as returned by GET /api/v1/categories (Story 3.4). parentCategoryId is null for top-level categories.
 */
public record CategoryResponse(
    UUID id,
    String categoryName,
    UUID parentCategoryId,
    String colorHex,
    String iconName,
    boolean systemDefault,
    OffsetDateTime createdAt
) {
    public static CategoryResponse of(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getCategoryName(),
            category.getParentCategoryId(),
            category.getColorHex(),
            category.getIconName(),
            category.isSystemDefault(),
            category.getCreatedAt()
        );
    }
}
