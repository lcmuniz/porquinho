package com.porquinho.controller;

import com.porquinho.dto.DataExportResponse;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.DataExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for data export operations (LGPD compliance).
 * Separated from AuthController to maintain clean endpoint grouping.
 */
@RestController
@RequestMapping("/api/v1/data-export")
public class DataExportController {

    private final DataExportService dataExportService;
    private final AuditLogService auditLogService;

    public DataExportController(DataExportService dataExportService,
                                AuditLogService auditLogService) {
        this.dataExportService = dataExportService;
        this.auditLogService = auditLogService;
    }

    /**
     * Request a new data export.
     * Rate limited to 1 export per hour.
     */
    @PostMapping
    public ResponseEntity<DataExportResponse> requestExport(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String ipAddress = request.getRemoteAddr();

        auditLogService.log("data_export_requested", userId, ipAddress);

        DataExportResponse response = dataExportService.requestExport(userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get the status of the most recent export.
     */
    @GetMapping("/status")
    public ResponseEntity<DataExportResponse> getExportStatus(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        DataExportResponse response = dataExportService.getExportStatus(userId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Download an export file by its token.
     */
    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> downloadExport(
            @PathVariable("token") String token,
            HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();

        Resource resource = dataExportService.downloadExport(token, ipAddress);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"porquinho-data-export.json\"")
                .body(resource);
    }
}
