package com.porquinho.controller;

import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.DashboardLayer2Response;
import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.service.DashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Dashboard GPS (Epic 7). The user always comes from the JWT. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
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
        DashboardService.DetailsFilter filter = new DashboardService.DetailsFilter(from, to, categoryIds, uncategorized,
            accountIds, creditCardIds, q, sort);
        return ResponseEntity.ok(dashboardService.details(UUID.fromString(jwt.getSubject()), filter, page));
    }

    /** Story 7.6: the whole filtered list (not just a page), same order, as CSV. */
    @GetMapping(value = "/layer3/export", produces = "text/csv")
    public ResponseEntity<String> layer3Export(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> categoryIds,
            @RequestParam(defaultValue = "false") boolean uncategorized,
            @RequestParam(required = false) List<UUID> accountIds,
            @RequestParam(required = false) List<UUID> creditCardIds,
            @RequestParam(required = false) String q, @RequestParam(required = false) String sort) {
        DashboardService.DetailsFilter filter = new DashboardService.DetailsFilter(from, to, categoryIds, uncategorized,
            accountIds, creditCardIds, q, sort);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transacoes.csv\"")
            .body(dashboardService.detailsCsv(UUID.fromString(jwt.getSubject()), filter));
    }
}
