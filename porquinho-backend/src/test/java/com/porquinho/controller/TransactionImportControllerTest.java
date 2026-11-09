package com.porquinho.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.ImportReviewActionResponse;
import com.porquinho.dto.ImportTransactionResponse;
import com.porquinho.dto.TransactionImportResponse;
import com.porquinho.dto.UpdateImportCategoryRequest;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.TransactionCore;
import com.porquinho.service.TransactionImportReviewService;
import com.porquinho.service.TransactionImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionImportController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class TransactionImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionImportService transactionImportService;

    @MockBean
    private TransactionImportReviewService transactionImportReviewService;

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

    @Test
    void transactionsCarryTheFieldsOfTheReview() throws Exception {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(transactionImportReviewService.list(userId, id)).thenReturn(List.of(new ImportTransactionResponse(
            UUID.randomUUID(), LocalDate.of(2026, 11, 3), "LOJA 03/10", new BigDecimal("189.90"), "DEBIT", "REGULAR",
            new ImportTransactionResponse.Category(categoryId, "Mercado", "Alimentação"), "AI", 92, "PENDING", true,
            new ObjectMapper().readTree("{\"direction\":\"SENT\",\"counterpart_name\":\"MARIA\"}"), true, 3, 10,
            false)));

        mockMvc.perform(get("/api/v1/imports/" + id + "/transactions").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value("2026-11-03"))
            .andExpect(jsonPath("$[0].description").value("LOJA 03/10"))
            .andExpect(jsonPath("$[0].amount").value(189.90))
            .andExpect(jsonPath("$[0].type").value("DEBIT"))
            .andExpect(jsonPath("$[0].kind").value("REGULAR"))
            .andExpect(jsonPath("$[0].category.id").value(categoryId.toString()))
            .andExpect(jsonPath("$[0].category.name").value("Mercado"))
            .andExpect(jsonPath("$[0].category.group").value("Alimentação"))
            .andExpect(jsonPath("$[0].source").value("AI"))
            .andExpect(jsonPath("$[0].confidence").value(92))
            .andExpect(jsonPath("$[0].reviewStatus").value("PENDING"))
            .andExpect(jsonPath("$[0].isPix").value(true))
            .andExpect(jsonPath("$[0].pixDetails.counterpart_name").value("MARIA"))
            .andExpect(jsonPath("$[0].isInstallment").value(true))
            .andExpect(jsonPath("$[0].installmentNumber").value(3))
            .andExpect(jsonPath("$[0].installmentTotal").value(10))
            .andExpect(jsonPath("$[0].isFutureInstallment").value(false));
    }

    @Test
    void approvalAndDiscardGoToTheJwtUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionImportReviewService.approveHighConfidence(eq(userId), eq(id), anyString()))
            .thenReturn(new ImportReviewActionResponse(id, "REVIEW", 3));
        when(transactionImportReviewService.discard(eq(userId), eq(id), anyString()))
            .thenReturn(new ImportReviewActionResponse(id, "DISCARDED", 5));

        mockMvc.perform(post("/api/v1/imports/" + id + "/approve-high-confidence").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW"))
            .andExpect(jsonPath("$.affectedTransactions").value(3));
        mockMvc.perform(post("/api/v1/imports/" + id + "/discard").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISCARDED"))
            .andExpect(jsonPath("$.affectedTransactions").value(5));
    }

    @Test
    void reviewRoutesOfAnotherUsersImportAre404() throws Exception {
        UUID id = UUID.randomUUID();
        ResourceNotFoundException notFound = new ResourceNotFoundException(TransactionImportService.NOT_FOUND_MESSAGE);
        when(transactionImportReviewService.list(userId, id)).thenThrow(notFound);
        when(transactionImportReviewService.approveHighConfidence(eq(userId), eq(id), anyString())).thenThrow(notFound);
        when(transactionImportReviewService.discard(eq(userId), eq(id), anyString())).thenThrow(notFound);

        mockMvc.perform(get("/api/v1/imports/" + id + "/transactions").with(authentication(authToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Importação não encontrada"));
        mockMvc.perform(post("/api/v1/imports/" + id + "/approve-high-confidence").with(authentication(authToken)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/imports/" + id + "/discard").with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }

    @Test
    void approvingOrDiscardingOutsideReviewIs409() throws Exception {
        UUID id = UUID.randomUUID();
        ConflictException conflict = new ConflictException(TransactionImportReviewService.NOT_IN_REVIEW_MESSAGE);
        when(transactionImportReviewService.approveHighConfidence(eq(userId), eq(id), anyString())).thenThrow(conflict);
        when(transactionImportReviewService.discard(eq(userId), eq(id), anyString())).thenThrow(conflict);

        mockMvc.perform(post("/api/v1/imports/" + id + "/approve-high-confidence").with(authentication(authToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Esta importação não está em revisão"));
        mockMvc.perform(post("/api/v1/imports/" + id + "/discard").with(authentication(authToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Esta importação não está em revisão"));
    }

    // ---- Story 5.10 ----

    private String categoryBody(UUID row, UUID category) {
        return "{\"transactionIds\":[\"" + row + "\"],\"categoryId\":\"" + category + "\"}";
    }

    @Test
    void categoryChangeGoesToTheJwtUser() throws Exception {
        UUID id = UUID.randomUUID();
        UUID row = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        when(transactionImportReviewService.updateCategory(eq(userId), eq(id),
            eq(new UpdateImportCategoryRequest(List.of(row), category)), anyString()))
            .thenReturn(new ImportReviewActionResponse(id, "REVIEW", 1));

        mockMvc.perform(patch("/api/v1/imports/" + id + "/transactions/category").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(categoryBody(row, category)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW"))
            .andExpect(jsonPath("$.affectedTransactions").value(1));
    }

    @Test
    void categoryChangeWithoutIdsOrCategoryIs400WithoutCallingTheService() throws Exception {
        UUID id = UUID.randomUUID();
        String url = "/api/v1/imports/" + id + "/transactions/category";

        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[],\"categoryId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Escolha ao menos uma transação"));
        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[\"" + UUID.randomUUID() + "\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Escolha uma categoria"));
        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[null],\"categoryId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[\"x\"],\"categoryId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionImportReviewService);
    }

    @Test
    void categoryChangeAboveTheLimitOfIdsIs400WithoutCallingTheService() throws Exception {
        String ids = java.util.stream.Stream.generate(UUID::randomUUID).limit(5001)
            .map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(patch("/api/v1/imports/" + UUID.randomUUID() + "/transactions/category")
                .with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[" + ids + "],\"categoryId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Altere no máximo 5000 transações de uma vez"));
        verifyNoInteractions(transactionImportReviewService);
    }

    @Test
    void categoryChangeErrorsKeepTheirStatusAndMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionImportReviewService.updateCategory(eq(userId), eq(id), any(), anyString()))
            .thenThrow(new ConflictException(TransactionImportReviewService.NOT_IN_REVIEW_MESSAGE))
            .thenThrow(new BadRequestException(TransactionCore.GROUP_NOT_ALLOWED_MESSAGE))
            .thenThrow(new ResourceNotFoundException(TransactionImportReviewService.TRANSACTION_NOT_FOUND_MESSAGE));
        String url = "/api/v1/imports/" + id + "/transactions/category";
        String body = categoryBody(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Escolha uma categoria, não um grupo"));
        mockMvc.perform(patch(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void completeGoesToTheJwtUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionImportReviewService.complete(eq(userId), eq(id), anyString()))
            .thenReturn(new ImportReviewActionResponse(id, "COMPLETED", 4));

        mockMvc.perform(post("/api/v1/imports/" + id + "/complete").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.affectedTransactions").value(4));
    }

    @Test
    void completeErrorsKeepTheirStatusAndMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionImportReviewService.complete(eq(userId), eq(id), anyString()))
            .thenThrow(new ConflictException(TransactionImportReviewService.ACCOUNT_REMOVED_MESSAGE))
            .thenThrow(new BadRequestException(TransactionImportReviewService.BALANCE_OUT_OF_RANGE_MESSAGE));

        mockMvc.perform(post("/api/v1/imports/" + id + "/complete").with(authentication(authToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value(TransactionImportReviewService.ACCOUNT_REMOVED_MESSAGE));
        mockMvc.perform(post("/api/v1/imports/" + id + "/complete").with(authentication(authToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(TransactionImportReviewService.BALANCE_OUT_OF_RANGE_MESSAGE));
    }
}
