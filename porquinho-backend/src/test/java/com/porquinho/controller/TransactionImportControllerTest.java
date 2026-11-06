package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.TransactionImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionImportController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class TransactionImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionImportService transactionImportService;

    private UUID userId;
    private UUID accountId;
    private TestingAuthenticationToken authToken;
    private final MockMultipartFile ofx = new MockMultipartFile("file", "nubank-conta.ofx", "application/x-ofx",
        "OFXHEADER:100\n<OFX><STMTRS>".getBytes(StandardCharsets.US_ASCII));

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    private TransactionImportResponse response() {
        return new TransactionImportResponse(UUID.randomUUID(), accountId, null, "Nubank", "nubank-conta.ofx", "OFX",
            null, 4, "PROCESSING", 0, 0, 0, 0, 0, 0, 0, null, OffsetDateTime.parse("2026-11-04T15:00:00Z"));
    }

    @Test
    void postUploadsForTheJwtUserAndReturns201() throws Exception {
        when(transactionImportService.create(eq(userId), eq(accountId), isNull(), any(MultipartFile.class), anyString()))
            .thenReturn(response());

        mockMvc.perform(multipart("/api/v1/imports").file(ofx).param("accountId", accountId.toString())
                .with(authentication(authToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.fileName").value("nubank-conta.ofx"))
            .andExpect(jsonPath("$.targetName").value("Nubank"))
            .andExpect(jsonPath("$.fileType").value("OFX"));

        verify(transactionImportService).create(eq(userId), eq(accountId), isNull(), any(MultipartFile.class), anyString());
    }

    @Test
    void serviceValidationErrorIs400ProblemDetail() throws Exception {
        when(transactionImportService.create(eq(userId), eq(accountId), isNull(), any(MultipartFile.class), anyString()))
            .thenThrow(new BadRequestException(TransactionImportService.CARD_FILE_TO_ACCOUNT_MESSAGE));

        mockMvc.perform(multipart("/api/v1/imports").file(ofx).param("accountId", accountId.toString())
                .with(authentication(authToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Este arquivo é de cartão de crédito. Escolha um cartão como destino."));
    }

    @Test
    void missingTargetReachesTheServiceAsNulls() throws Exception {
        when(transactionImportService.create(eq(userId), isNull(), isNull(), any(MultipartFile.class), anyString()))
            .thenThrow(new BadRequestException(TransactionImportService.TARGET_REQUIRED_MESSAGE));

        mockMvc.perform(multipart("/api/v1/imports").file(ofx).with(authentication(authToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(TransactionImportService.TARGET_REQUIRED_MESSAGE));
    }

    @Test
    void targetNotFoundIs404() throws Exception {
        when(transactionImportService.create(eq(userId), eq(accountId), isNull(), any(MultipartFile.class), anyString()))
            .thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(multipart("/api/v1/imports").file(ofx).param("accountId", accountId.toString())
                .with(authentication(authToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }

    @Test
    void uploadAboveTheMultipartLimitIs400WithTheSizeMessage() throws Exception {
        when(transactionImportService.create(eq(userId), eq(accountId), isNull(), any(MultipartFile.class), anyString()))
            .thenThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        mockMvc.perform(multipart("/api/v1/imports").file(ofx).param("accountId", accountId.toString())
                .with(authentication(authToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Arquivo muito grande (máximo 10MB)"));
    }

    @Test
    void invalidTargetIdIs400WithoutCallingTheService() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports").file(ofx).param("accountId", "nao-e-uuid")
                .with(authentication(authToken)))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionImportService);
    }

    @Test
    void getListsTheRecentImportsOfTheJwtUser() throws Exception {
        when(transactionImportService.listRecent(userId)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/imports").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fileName").value("nubank-conta.ofx"))
            .andExpect(jsonPath("$[0].targetName").value("Nubank"));
    }

    @Test
    void getByIdReturnsTheImportOfTheJwtUser() throws Exception {
        TransactionImportResponse r = response();
        TransactionImportResponse detected = new TransactionImportResponse(r.id(), r.accountId(), null, "Nubank",
            "nubank-conta.ofx", "OFX", "NUBANK", 4, "PROCESSING", 0, 0, 0, 0, 0, 40, 10, null, r.createdAt());
        when(transactionImportService.get(userId, r.id())).thenReturn(detected);

        mockMvc.perform(get("/api/v1/imports/" + r.id()).with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(r.id().toString()))
            .andExpect(jsonPath("$.bankName").value("NUBANK"))
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.classificationTotal").value(40))
            .andExpect(jsonPath("$.classifiedCount").value(10));
    }

    @Test
    void getByIdOfAnotherUsersImportIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionImportService.get(userId, id))
            .thenThrow(new ResourceNotFoundException(TransactionImportService.NOT_FOUND_MESSAGE));

        mockMvc.perform(get("/api/v1/imports/" + id).with(authentication(authToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Importação não encontrada"));
    }
}
