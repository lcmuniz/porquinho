package com.porquinho.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.porquinho.service.CategoryOptions;
import com.porquinho.service.CategoryService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * POST /api/v1/categories (Story 3.5): a custom category inside one of the user's groups. The name is stripped
 * of leading/trailing whitespace (Unicode spaces included) before validation. The color and the icon must come
 * from GET /api/v1/categories/options. parentId is required: creating groups comes in Epic 6 (decision A).
 */
public record CreateCategoryRequest(
    @NotBlank(message = CategoryService.NAME_REQUIRED_MESSAGE)
    @Size(max = 50, message = CategoryService.NAME_TOO_LONG_MESSAGE)
    String name,

    @NotBlank(message = CategoryService.COLOR_INVALID_MESSAGE)
    String colorHex,

    @NotBlank(message = CategoryService.ICON_INVALID_MESSAGE)
    String iconName,

    @NotNull(message = CategoryService.PARENT_REQUIRED_MESSAGE)
    UUID parentId
) {
    public CreateCategoryRequest {
        name = CategoryService.cleanName(name);
    }

    /** Bean Validation property "colorAllowed"; a missing color is reported by @NotBlank instead. */
    @JsonIgnore
    @AssertTrue(message = CategoryService.COLOR_INVALID_MESSAGE)
    public boolean isColorAllowed() {
        return colorHex == null || colorHex.isBlank() || CategoryOptions.isAllowedColor(colorHex);
    }

    /** Bean Validation property "iconAllowed"; a missing icon is reported by @NotBlank instead. */
    @JsonIgnore
    @AssertTrue(message = CategoryService.ICON_INVALID_MESSAGE)
    public boolean isIconAllowed() {
        return iconName == null || iconName.isBlank() || CategoryOptions.isAllowedIcon(iconName);
    }
}
