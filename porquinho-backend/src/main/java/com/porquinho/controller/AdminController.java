package com.porquinho.controller;

import com.porquinho.dto.AdminAccessResponse;
import com.porquinho.dto.AdminAlertsResponse;
import com.porquinho.dto.AdminAiBankDetailResponse;
import com.porquinho.dto.AdminAiMetricsResponse;
import com.porquinho.dto.AdminHealthResponse;
import com.porquinho.dto.AdminImportBankDetailResponse;
import com.porquinho.dto.AdminImportMetricsResponse;
import com.porquinho.dto.AdminUserMetricsResponse;
import com.porquinho.service.AdminService;
import com.porquinho.service.AiMetricsService;
import com.porquinho.service.ImportMetricsService;
import com.porquinho.service.SystemAlertService;
import com.porquinho.service.UserMetricsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Story 10.1: admin API. Every route except {@code access} answers 403 to non-admins: {@code AdminAccessInterceptor}
 * checks it before the handler, so it also holds in the test profile.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ImportMetricsService importMetricsService;
    private final AiMetricsService aiMetricsService;
    private final UserMetricsService userMetricsService;
    private final SystemAlertService systemAlertService;

    public AdminController(AdminService adminService, ImportMetricsService importMetricsService,
                           AiMetricsService aiMetricsService, UserMetricsService userMetricsService,
                           SystemAlertService systemAlertService) {
        this.adminService = adminService;
        this.importMetricsService = importMetricsService;
        this.aiMetricsService = aiMetricsService;
        this.userMetricsService = userMetricsService;
        this.systemAlertService = systemAlertService;
    }

    @GetMapping("/access")
    public AdminAccessResponse access(@AuthenticationPrincipal Jwt jwt) {
        return new AdminAccessResponse(adminService.isAdmin(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/health")
    public AdminHealthResponse health() {
        return adminService.health();
    }

    // Story 10.2: every parameter is optional so the 403 comes before any 400

    @GetMapping("/imports")
    public AdminImportMetricsResponse imports(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(required = false) String bank) {
        return importMetricsService.metrics(importMetricsService.filters(from, to, bank, null));
    }

    @GetMapping("/imports/detail")
    public AdminImportBankDetailResponse importDetail(@RequestParam(required = false) String bank,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to,
                                                      @RequestParam(required = false) String status) {
        return importMetricsService.detail(importMetricsService.filters(from, to, bank, status));
    }

    @GetMapping(value = "/imports/export", produces = "text/csv")
    public ResponseEntity<String> importExport(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) String bank,
                                               @RequestParam(required = false) String status) {
        ImportMetricsService.Filters filters = importMetricsService.filters(from, to, bank, status);
        String name = "erros_importacao_" + filters.from() + "_" + filters.to() + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .body(importMetricsService.csv(filters));
    }

    // Story 10.3: same filters as 10.2, without the status

    @GetMapping("/ai")
    public AdminAiMetricsResponse ai(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String bank) {
        return aiMetricsService.metrics(importMetricsService.filters(from, to, bank, null));
    }

    @GetMapping("/ai/detail")
    public AdminAiBankDetailResponse aiDetail(@RequestParam(required = false) String bank,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return aiMetricsService.detail(importMetricsService.filters(from, to, bank, null));
    }

    @GetMapping(value = "/ai/export", produces = "text/csv")
    public ResponseEntity<String> aiExport(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) String bank) {
        ImportMetricsService.Filters filters = importMetricsService.filters(from, to, bank, null);
        String name = "precisao_ia_" + filters.from() + "_" + filters.to() + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .body(aiMetricsService.csv(filters));
    }

    // Story 10.4: no filter, the numbers are "now"

    @GetMapping("/users")
    public AdminUserMetricsResponse users() {
        return userMetricsService.metrics();
    }

    @GetMapping(value = "/users/export", produces = "text/csv")
    public ResponseEntity<String> usersExport() {
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + userMetricsService.fileName() + "\"")
            .body(userMetricsService.csv());
    }

    // Story 10.5: the alerts of the critical metrics

    @GetMapping("/alerts")
    public AdminAlertsResponse alerts() {
        return systemAlertService.list();
    }

    @PostMapping("/alerts/{id}/resolve")
    public AdminAlertsResponse.Alert resolveAlert(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return systemAlertService.resolve(id, UUID.fromString(jwt.getSubject()));
    }
}
