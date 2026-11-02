package com.porquinho.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.porquinho.service.CategoryService;
import com.porquinho.service.CategorySuggestions;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/onboarding/me/categories (Story 3.4): the keys of the chosen suggestions.
 * At least one key, all of them from the catalog (repeated keys are accepted and ignored by the service).
 */
public record CompleteCategoriesRequest(
    @NotEmpty(message = CategoryService.EMPTY_KEYS_MESSAGE)
    @Size(max = 50, message = "Envie no máximo 50 categorias de uma vez")
    List<String> keys
) {

    /** Bean Validation property "keysKnown"; an empty or null list is reported by @NotEmpty instead. */
    @JsonIgnore
    @AssertTrue(message = CategoryService.UNKNOWN_KEY_MESSAGE)
    public boolean isKeysKnown() {
        return keys == null || keys.stream().allMatch(CategorySuggestions::isKnown);
    }
}
