package com.porquinho.controller;

import com.porquinho.dto.DataExportRequest;
import com.porquinho.dto.DataExportResponse;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.DataExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * REST controller for data export operations (LGPD compliance).
 * Separated from AuthController to maintain clean endpoint grouping.
 */
@RestController
@RequestMapping("/api/v1/data-export")
public class DataExportController {

    private final DataExportService dataExportService;
    private final String frontendUrl;

    public DataExportController(DataExportService dataExportService,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.dataExportService = dataExportService;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Request a new data export.
     * Rate limited to 1 export per hour. Body: the ZIP password (min 8 chars, else 400 before any row/audit).
     */
    @PostMapping
    public ResponseEntity<DataExportResponse> requestExport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DataExportRequest body,
            HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String ipAddress = request.getRemoteAddr();

        DataExportResponse response = dataExportService.requestExport(userId, ipAddress, body.password());
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
    public ResponseEntity<?> downloadExport(
            @PathVariable("token") String token,
            HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();

        Resource resource;
        try {
            resource = dataExportService.downloadExport(token, ipAddress);
        } catch (ResourceNotFoundException e) {
            // O link é aberto direto do e-mail no navegador: página legível em vez do JSON de erro da API
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                    .body(downloadErrorPage(e.getMessage()));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"porquinho-dados-" + LocalDate.now(ZoneId.of("America/Sao_Paulo")) + ".zip\"")
                .body(resource);
    }

    private String downloadErrorPage(String message) {
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Porquinho - Exportação de dados</title></head><body>"
                + "<h1>Não foi possível baixar a exportação</h1>"
                + "<p>" + HtmlUtils.htmlEscape(message, "UTF-8") + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(frontendUrl + "/auth/data-export", "UTF-8") + "\">Abrir o Porquinho</a></p>"
                + "</body></html>";
    }
}
