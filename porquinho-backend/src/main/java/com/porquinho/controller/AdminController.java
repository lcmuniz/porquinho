package com.porquinho.controller;

import com.porquinho.dto.AdminAccessResponse;
import com.porquinho.dto.AdminHealthResponse;
import com.porquinho.service.AdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Story 10.1: admin API. Every route except {@code access} answers 403 to non-admins (checked here, so it also holds
 * in the test profile). The role is read from the database on each call: the Supabase JWT does not carry it.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
}
