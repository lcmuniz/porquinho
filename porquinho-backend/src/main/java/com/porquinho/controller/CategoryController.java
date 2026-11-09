package com.porquinho.controller;

import com.porquinho.dto.CategoryResponse;
import com.porquinho.dto.CreateCategoryRequest;
import com.porquinho.dto.UpdateCategoryRequest;
import com.porquinho.service.CategoryOptions;
import com.porquinho.service.CategoryService;
import com.porquinho.service.CategorySuggestions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Category endpoints (Story 3.4: list and suggestions; Story 3.5: options, create, update and remove; Story 6.2:
 * create a group and move a child to another group). Used by
 * onboarding steps 4 and 5 and, later, by the budget (Epic 6).
 * The user always comes from the JWT.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** The user's active categories (parents and children), by name then id. */
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

    /** Allowed palette colors and Lucide icon names for create/update (same for every user). */
    @GetMapping("/options")
    public ResponseEntity<CategoryOptions.Options> options() {
        return ResponseEntity.ok(CategoryOptions.OPTIONS);
    }

    /**
     * 201 with the new custom category or, without parentId, the new group (is_system_default=false); 400 when
     * invalid; 409 on a repeated name.
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateCategoryRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.of(categoryService.create(
            userId, request.name(), request.colorHex(), request.iconName(), request.parentId())));
    }

    /** 200 with the updated (or moved) category; 400 when invalid; 404 when not the user's; 409 on a repeated name. */
    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                                   @Valid @RequestBody UpdateCategoryRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(CategoryResponse.of(categoryService.update(
            userId, id, request.name(), request.colorHex(), request.iconName(), request.parentId())));
    }

    /** Soft delete. 204; 404 when not the user's; 409 for a group with children or the last child. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        categoryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
