package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.DataExportResponse;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.AuditLogService;
import com.porquinho.service.DataExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataExportController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class DataExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataExportService dataExportService;

    @MockBean
    private AuditLogService auditLogService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    @Test
    void requestExportShouldReturn201() throws Exception {
        DataExportResponse response = new DataExportResponse(
                UUID.randomUUID(), "PROCESSING", LocalDateTime.now(),
                LocalDateTime.now().plusHours(24), null);

        when(dataExportService.requestExport(eq(userId), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/data-export")
                .with(authentication(authToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(auditLogService).log(eq("data_export_requested"), eq(userId), anyString());
    }

    @Test
    void requestExportShouldReturn429WhenRateLimited() throws Exception {
        when(dataExportService.requestExport(eq(userId), anyString()))
                .thenThrow(new RateLimitExceededException("Rate limit exceeded"));

        mockMvc.perform(post("/api/v1/data-export")
                .with(authentication(authToken)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void getExportStatusShouldReturn200WhenExportExists() throws Exception {
        DataExportResponse response = new DataExportResponse(
                UUID.randomUUID(), "READY", LocalDateTime.now(),
                LocalDateTime.now().plusHours(24), "/api/v1/data-export/download/" + UUID.randomUUID());

        when(dataExportService.getExportStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/data-export/status")
                .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());
    }

    @Test
    void getExportStatusShouldReturn204WhenNoExport() throws Exception {
        when(dataExportService.getExportStatus(userId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/data-export/status")
                .with(authentication(authToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void downloadExportShouldReturnFileWithCorrectHeaders() throws Exception {
        String token = UUID.randomUUID().toString();
        byte[] content = "{\"test\": true}".getBytes();
        ByteArrayResource resource = new ByteArrayResource(content);

        when(dataExportService.downloadExport(eq(token), anyString())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/data-export/download/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"porquinho-data-export.json\""))
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void downloadExportShouldReturn404WhenNotFound() throws Exception {
        String token = UUID.randomUUID().toString();
        when(dataExportService.downloadExport(eq(token), anyString()))
                .thenThrow(new ResourceNotFoundException("Export not found"));

        mockMvc.perform(get("/api/v1/data-export/download/" + token))
                .andExpect(status().isNotFound());
    }

}
