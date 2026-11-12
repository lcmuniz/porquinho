package com.porquinho.controller;

import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
