package com.porquinho.controller;

import com.porquinho.dto.AdminAccessResponse;
import com.porquinho.dto.AdminAiBankDetailResponse;
import com.porquinho.dto.AdminAiMetricsResponse;
import com.porquinho.dto.AdminHealthResponse;
import com.porquinho.dto.AdminImportBankDetailResponse;
import com.porquinho.dto.AdminImportMetricsResponse;
import com.porquinho.dto.AdminUserMetricsResponse;
import com.porquinho.service.AdminService;
import com.porquinho.service.AiMetricsService;
import com.porquinho.service.ImportMetricsService;
import com.porquinho.service.UserMetricsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Story 10.1: admin API. Every route except {@code access} answers 403 to non-admins (checked here, so it also holds
 * in the test profile). The role is read from the database on each call: the Supabase JWT does not carry it.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ImportMetricsService importMetricsService;
    private final AiMetricsService aiMetricsService;
    private final UserMetricsService userMetricsService;

    public AdminController(AdminService adminService, ImportMetricsService importMetricsService,
                           AiMetricsService aiMetricsService, UserMetricsService userMetricsService) {
        this.adminService = adminService;
        this.importMetricsService = importMetricsService;
        this.aiMetricsService = aiMetricsService;
        this.userMetricsService = userMetricsService;
    }

    @GetMapping("/access")
    public AdminAccessResponse access(@AuthenticationPrincipal Jwt jwt) {
        return new AdminAccessResponse(adminService.isAdmin(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/health")
    public AdminHealthResponse health(@AuthenticationPrincipal Jwt jwt) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return adminService.health();
    }

    // Story 10.2: every parameter is optional so the 403 comes before any 400

    @GetMapping("/imports")
    public AdminImportMetricsResponse imports(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(required = false) String bank) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return importMetricsService.metrics(importMetricsService.filters(from, to, bank, null));
    }

    @GetMapping("/imports/detail")
    public AdminImportBankDetailResponse importDetail(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) String bank,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to,
                                                      @RequestParam(required = false) String status) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return importMetricsService.detail(importMetricsService.filters(from, to, bank, status));
    }

    @GetMapping(value = "/imports/export", produces = "text/csv")
    public ResponseEntity<String> importExport(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) String bank,
                                               @RequestParam(required = false) String status) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        ImportMetricsService.Filters filters = importMetricsService.filters(from, to, bank, status);
        String name = "erros_importacao_" + filters.from() + "_" + filters.to() + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .body(importMetricsService.csv(filters));
    }

    // Story 10.3: same filters as 10.2, without the status

    @GetMapping("/ai")
    public AdminAiMetricsResponse ai(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String bank) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return aiMetricsService.metrics(importMetricsService.filters(from, to, bank, null));
    }

    @GetMapping("/ai/detail")
    public AdminAiBankDetailResponse aiDetail(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String bank,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return aiMetricsService.detail(importMetricsService.filters(from, to, bank, null));
    }

    @GetMapping(value = "/ai/export", produces = "text/csv")
    public ResponseEntity<String> aiExport(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) String bank) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        ImportMetricsService.Filters filters = importMetricsService.filters(from, to, bank, null);
        String name = "precisao_ia_" + filters.from() + "_" + filters.to() + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .body(aiMetricsService.csv(filters));
    }

    // Story 10.4: no filter, the numbers are "now"

    @GetMapping("/users")
    public AdminUserMetricsResponse users(@AuthenticationPrincipal Jwt jwt) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return userMetricsService.metrics();
    }

    @GetMapping(value = "/users/export", produces = "text/csv")
    public ResponseEntity<String> usersExport(@AuthenticationPrincipal Jwt jwt) {
        adminService.requireAdmin(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + userMetricsService.fileName() + "\"")
            .body(userMetricsService.csv());
    }
}
