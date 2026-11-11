package com.porquinho.controller;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.CategoryBudgetHistoryResponse;
import com.porquinho.dto.ReallocateBudgetRequest;
import com.porquinho.dto.SaveBudgetRequest;
import com.porquinho.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Monthly budget per category (Story 6.3). The user always comes from the JWT; {month} is YYYY-MM (400 otherwise). */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /** Every active child category with its allocation (or null), spent and committed (null in a closed month). */
    @GetMapping("/{month}")
    public ResponseEntity<BudgetResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable("month") String month) {
        return ResponseEntity.ok(budgetService.get(UUID.fromString(jwt.getSubject()), month));
    }

    /** Replaces the month's allocations; 200 with the month recalculated; 400 without writing anything. */
    @PutMapping("/{month}")
    public ResponseEntity<BudgetResponse> save(@AuthenticationPrincipal Jwt jwt, @PathVariable("month") String month,
                                               @Valid @RequestBody SaveBudgetRequest request) {
        return ResponseEntity.ok(budgetService.save(UUID.fromString(jwt.getSubject()), month, request.allocations()));
    }

    /** Moves money between two categories of an open month (Story 6.5); 200 with the month recalculated; 400 otherwise. */
    @PostMapping("/{month}/reallocations")
    public ResponseEntity<BudgetResponse> reallocate(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable("month") String month,
                                                     @Valid @RequestBody ReallocateBudgetRequest request) {
        return ResponseEntity.ok(budgetService.reallocate(UUID.fromString(jwt.getSubject()), month, request));
    }

    /** Monthly history of a child category (Story 6.8); 404 for a group, removed or foreign; 400 on a malformed id. */
    @GetMapping("/categories/{categoryId}/history")
    public ResponseEntity<CategoryBudgetHistoryResponse> history(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable("categoryId") UUID categoryId) {
        return ResponseEntity.ok(budgetService.history(UUID.fromString(jwt.getSubject()), categoryId));
    }
}
