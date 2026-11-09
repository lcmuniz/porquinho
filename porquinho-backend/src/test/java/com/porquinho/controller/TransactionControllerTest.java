package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.TransactionResponse;
import com.porquinho.dto.TransactionUpdateRequest;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Story 5.12: the HTTP side of /api/v1/transactions. */
@WebMvcTest(TransactionController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    private static String body(String amount, String description) {
        return "{\"date\":\"2026-11-05\",\"description\":" + (description == null ? "null" : "\"" + description + "\"")
            + ",\"amount\":" + amount + ",\"type\":\"DEBIT\",\"accountId\":\"" + UUID.randomUUID() + "\"}";
    }

    private static TransactionResponse response(UUID id) {
        return new TransactionResponse(id, LocalDate.of(2026, 11, 5), "MERCADO", new BigDecimal("150.00"), "DEBIT",
            "REGULAR", null, "USER", 100, "CONFIRMED", true, null, true, 3, 10, false, UUID.randomUUID(), null,
            UUID.randomUUID(), 7, 1);
    }

    @Test
    void listPassesTheMonthOfTheJwtUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.list(userId, "2026-11")).thenReturn(List.of(response(id)));

        mockMvc.perform(get("/api/v1/transactions").param("month", "2026-11").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].isPix").value(true))
            .andExpect(jsonPath("$[0].isInstallment").value(true))
            .andExpect(jsonPath("$[0].remainingInstallments").value(7));
    }

    @Test
    void updateReturnsTheEditedTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.update(eq(userId), eq(id), any(TransactionUpdateRequest.class), anyString()))
            .thenReturn(response(id));

        mockMvc.perform(put("/api/v1/transactions/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("150.00", "  MERCADO   DA  ESQUINA ")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
        verify(transactionService).update(eq(userId), eq(id),
            org.mockito.ArgumentMatchers.argThat(r -> r.description().equals("MERCADO DA ESQUINA")), anyString());
    }

    @Test
    void invalidAmountOrDescriptionIs400WithoutCallingTheService() throws Exception {
        String url = "/api/v1/transactions/" + UUID.randomUUID();
        for (String amount : List.of("0", "-10", "0.001", "10000000000000.00")) {
            mockMvc.perform(put(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(amount, "MERCADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        }
        mockMvc.perform(put(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content(body("10", "   ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Informe a descrição"));
        mockMvc.perform(put(url).with(authentication(authToken)).contentType(MediaType.APPLICATION_JSON)
                .content(body("10", "x".repeat(501))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionService);
    }

    @Test
    void transactionNotAvailableIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.update(eq(userId), eq(id), any(), anyString()))
            .thenThrow(new ResourceNotFoundException(TransactionService.NOT_FOUND_MESSAGE));
        doThrow(new ResourceNotFoundException(TransactionService.NOT_FOUND_MESSAGE))
            .when(transactionService).delete(eq(userId), eq(id), anyString());

        mockMvc.perform(put("/api/v1/transactions/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("10", "MERCADO")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Transação não encontrada"));
        mockMvc.perform(delete("/api/v1/transactions/" + id).with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteIs204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/transactions/" + id).with(authentication(authToken)))
            .andExpect(status().isNoContent());
        verify(transactionService).delete(eq(userId), eq(id), anyString());
    }

    @Test
    void removeFutureInstallmentsReturnsTheCount() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.removeFutureInstallments(eq(userId), eq(id), anyString())).thenReturn(7);

        mockMvc.perform(post("/api/v1/transactions/" + id + "/remove-future-installments")
                .with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.removedInstallments").value(7));
    }
}
