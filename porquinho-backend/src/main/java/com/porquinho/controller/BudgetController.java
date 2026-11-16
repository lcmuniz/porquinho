package com.porquinho.controller;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.CategoryBudgetHistoryResponse;
import com.porquinho.dto.ReallocateBudgetRequest;
import com.porquinho.dto.SaveBudgetRequest;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.BudgetReportService;
import com.porquinho.service.BudgetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Monthly budget per category (Story 6.3). The user always comes from the JWT; {month} is YYYY-MM (400 otherwise). */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetReportService budgetReportService;
    private final AuditLogService auditLogService;

    public BudgetController(BudgetService budgetService, BudgetReportService budgetReportService,
                            AuditLogService auditLogService) {
        this.budgetService = budgetService;
        this.budgetReportService = budgetReportService;
        this.auditLogService = auditLogService;
    }

    /**
     * Story 9.2: budget report of [from, to] (YYYY-MM, 1 to 12 months) as CSV; audited as "budget_export". The literal
     * path wins over /{month}. 400 on an invalid range, nothing audited.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(defaultValue = "false") boolean hierarchy,
                                         @RequestParam(defaultValue = "false") boolean averages,
                                         HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String csv = budgetReportService.csv(userId, from, to, hierarchy, averages);
        auditLogService.log("budget_export", userId, request.getRemoteAddr(),
            AuditLogService.metadata("from", from, "to", to));
        String name = from.equals(to) ? "orcamento_" + from : "orcamento_" + from + "_" + to;
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
            .body(csv);
    }

    /** Every active child category with its allocation (or null), spent and committed (null in a closed month). */
    @GetMapping("/{month}")
    public ResponseEntity<BudgetResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable("month") String month) {
        return ResponseEntity.ok(budgetService.get(UUID.fromString(jwt.getSubject()), month));
    }

    /** Replaces the month's allocations; 200 with the month recalculated; 400 without writing anything; 409 if stale. */
    @PutMapping("/{month}")
    public ResponseEntity<BudgetResponse> save(@AuthenticationPrincipal Jwt jwt, @PathVariable("month") String month,
                                               @Valid @RequestBody SaveBudgetRequest request) {
        return ResponseEntity.ok(budgetService.save(UUID.fromString(jwt.getSubject()), month, request.version(),
            request.allocations()));
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
