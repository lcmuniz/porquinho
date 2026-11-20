package com.porquinho.controller;

import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.DashboardLayer2Response;
import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.TransactionResponse;
import com.porquinho.service.DashboardDetailsService;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Dashboard GPS (Epic 7). The user always comes from the JWT. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardDetailsService detailsService;
    private final AuditLogService auditLogService;

    public DashboardController(DashboardService dashboardService, DashboardDetailsService detailsService,
                               AuditLogService auditLogService) {
        this.dashboardService = dashboardService;
        this.detailsService = detailsService;
        this.auditLogService = auditLogService;
    }

    /** Story 7.2: the financial health traffic light of the current month. */
    @GetMapping("/health")
    public ResponseEntity<FinancialHealthResponse> health(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(dashboardService.health(UUID.fromString(jwt.getSubject())));
    }

    /** Story 7.3: the 5 essential metrics of the current month. */
    @GetMapping("/layer1")
    public ResponseEntity<DashboardLayer1Response> layer1(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(dashboardService.overview(UUID.fromString(jwt.getSubject())));
    }

    /** Story 7.5: spending per category of the current month and the last 12 months' trends. */
    @GetMapping("/layer2")
    public ResponseEntity<DashboardLayer2Response> layer2(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(dashboardService.tactical(UUID.fromString(jwt.getSubject())));
    }

    /** Story 7.5: the current month's transactions of one slice of the layer 2 (no {@code categoryId} = uncategorized). */
    @GetMapping("/layer2/drilldown")
    public ResponseEntity<List<TransactionResponse>> layer2Drilldown(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID categoryId) {
        return ResponseEntity.ok(dashboardService.drilldown(UUID.fromString(jwt.getSubject()), categoryId));
    }

    /** Layer 3 "Maiores gastos": the current month's 10 largest outflows. */
    @GetMapping("/layer3/largest")
    public ResponseEntity<List<TransactionResponse>> layer3Largest(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(dashboardService.largestSpent(UUID.fromString(jwt.getSubject()), 10));
    }

    /** Story 7.6: page {@code page} (0-based, 50 rows) of the filtered transaction list. */
    @GetMapping("/layer3")
    public ResponseEntity<DashboardLayer3Response> layer3(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(defaultValue = "false") boolean uncategorized,
            @RequestParam(required = false) List<UUID> accountIds,
            @RequestParam(required = false) List<UUID> creditCardIds,
            @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page) {
        DashboardDetailsService.DetailsFilter filter = new DashboardDetailsService.DetailsFilter(from, to, categoryIds, uncategorized,
            accountIds, creditCardIds, q, sort);
        return ResponseEntity.ok(detailsService.details(UUID.fromString(jwt.getSubject()), filter, page));
    }

    /**
     * Story 7.6: the whole filtered list (not just a page), same order, as CSV. Story 9.1: the flags add future
     * installments and deleted rows; each export is audited ("transaction_export") and its row count goes in
     * X-Export-Count.
     */
    @GetMapping(value = "/layer3/export", produces = "text/csv")
    public void layer3Export(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(defaultValue = "false") boolean uncategorized,
            @RequestParam(required = false) List<UUID> accountIds,
            @RequestParam(required = false) List<UUID> creditCardIds,
            @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean includeFutureInstallments,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        UUID userId = UUID.fromString(jwt.getSubject());
        DashboardDetailsService.DetailsFilter filter = new DashboardDetailsService.DetailsFilter(from, to, categoryIds, uncategorized,
            accountIds, creditCardIds, q, sort);
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        // streamed (grupo C): headers go once the count is known; a bad filter throws before that and stays a 400
        long[] started = {-1};
        try {
            detailsService.detailsCsv(userId, filter, new DashboardDetailsService.ExportOptions(includeFutureInstallments,
                includeDeleted), count -> {
                    started[0] = count;
                    response.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8).toString());
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transacoes_" + today + ".csv\"");
                    response.setHeader("X-Export-Count", String.valueOf(count));
                    return response.getOutputStream();
                });
        } finally {
            // after the read-only transaction (an insert cannot run in it); a download cut halfway is still audited
            if (started[0] >= 0) {
                auditLogService.log("transaction_export", userId, request.getRemoteAddr(),
                    AuditLogService.metadata("count", started[0]));
            }
        }
    }
}
