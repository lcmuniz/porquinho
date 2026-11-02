package com.porquinho.controller;

import com.porquinho.dto.CategoryResponse;
import com.porquinho.service.CategoryService;
import com.porquinho.service.CategorySuggestions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Category endpoints (Story 3.4). Used by onboarding step 4 and, later, by the budget (Epic 6).
 * The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** The user's active categories (parents and children), oldest first. */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(categoryService.listActive(userId).stream().map(CategoryResponse::of).toList());
    }

    /** The fixed catalog of suggested categories, in 3 groups (same for every user). */
    @GetMapping("/suggestions")
    public ResponseEntity<List<CategorySuggestions.Group>> suggestions() {
        return ResponseEntity.ok(CategorySuggestions.GROUPS);
    }
}
