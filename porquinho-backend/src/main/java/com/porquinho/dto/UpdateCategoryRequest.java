package com.porquinho.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.porquinho.service.CategoryOptions;
import com.porquinho.service.CategoryService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/categories/{id} (Story 3.5): any combination of name, color and icon; a null field is left
 * unchanged. The parent cannot be changed here. The name is stripped like in {@link CreateCategoryRequest}
 * and, when sent, cannot end up empty.
 */
public record UpdateCategoryRequest(
    @Size(max = 50, message = CategoryService.NAME_TOO_LONG_MESSAGE)
    String name,

    String colorHex,

    String iconName
) {
    public UpdateCategoryRequest {
        name = CategoryService.cleanName(name);
    }

    /** Bean Validation property "nameNotBlank": a name that was sent must not be blank. */
    @JsonIgnore
    @AssertTrue(message = CategoryService.NAME_REQUIRED_MESSAGE)
    public boolean isNameNotBlank() {
        return name == null || !name.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = CategoryService.COLOR_INVALID_MESSAGE)
    public boolean isColorAllowed() {
        return colorHex == null || CategoryOptions.isAllowedColor(colorHex);
    }

    @JsonIgnore
    @AssertTrue(message = CategoryService.ICON_INVALID_MESSAGE)
    public boolean isIconAllowed() {
        return iconName == null || CategoryOptions.isAllowedIcon(iconName);
    }

    /** Bean Validation property "anyField": at least one field to change. */
    @JsonIgnore
    @AssertTrue(message = CategoryService.NOTHING_TO_UPDATE_MESSAGE)
    public boolean isAnyField() {
        return name != null || colorHex != null || iconName != null;
    }
}
