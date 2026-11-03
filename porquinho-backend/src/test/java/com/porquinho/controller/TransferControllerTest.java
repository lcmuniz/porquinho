package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.TransferResponse;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class TransferControllerTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    private UUID userId;
    private UUID fromId;
    private UUID toId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fromId = UUID.randomUUID();
        toId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    private static String quoted(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String body(UUID from, UUID to, String amount, LocalDate date, String description) {
        return body(UUID.randomUUID(), from, to, amount, date, description);
    }

    private String body(UUID id, UUID from, UUID to, String amount, LocalDate date, String description) {
        return "{\"id\":" + quoted(id) + ",\"fromAccountId\":" + quoted(from) + ",\"toAccountId\":" + quoted(to) + ",\"amount\":" + amount
            + ",\"transferDate\":" + quoted(date) + ",\"description\":" + description + "}";
    }

    private static LocalDate today() {
        return LocalDate.now(SAO_PAULO);
    }

    private TransferResponse response(String amount, String description) {
        return new TransferResponse(UUID.randomUUID(), fromId, "Nubank", toId, "Inter", new BigDecimal(amount),
            today(), description, OffsetDateTime.parse("2026-11-03T15:00:00Z"));
    }

    private TransferService.Result created(String amount, String description) {
        return new TransferService.Result(response(amount, description), true);
    }

    @Test
    void postCreatesTheTransferForTheJwtUserAndReturns201() throws Exception {
        when(transferService.create(eq(userId), any(CreateTransferRequest.class), anyString()))
            .thenReturn(created("200.00", "Reserva"));

        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "200.00", today(), "\"  Reserva \"")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fromAccountName").value("Nubank"))
            .andExpect(jsonPath("$.toAccountName").value("Inter"))
            .andExpect(jsonPath("$.amount").value(200.0))
            .andExpect(jsonPath("$.transferDate").value(today().toString()))
            .andExpect(jsonPath("$.description").value("Reserva"));

        ArgumentCaptor<CreateTransferRequest> captor = ArgumentCaptor.forClass(CreateTransferRequest.class);
        verify(transferService).create(eq(userId), captor.capture(), anyString());
        assertThat(captor.getValue().description()).isEqualTo("Reserva");
        assertThat(captor.getValue().fromAccountId()).isEqualTo(fromId);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void repeatedIdReturns200WithTheExistingTransfer() throws Exception {
        UUID transferId = UUID.randomUUID();
        when(transferService.create(eq(userId), any(CreateTransferRequest.class), anyString()))
            .thenReturn(new TransferService.Result(response("200.00", null), false));

        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(transferId, fromId, toId, "200.00", today(), "null")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fromAccountName").value("Nubank"));

        ArgumentCaptor<CreateTransferRequest> captor = ArgumentCaptor.forClass(CreateTransferRequest.class);
        verify(transferService).create(eq(userId), captor.capture(), anyString());
        assertThat(captor.getValue().id()).isEqualTo(transferId);
    }

    @Test
    void missingIdIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(null, fromId, toId, "10", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.id").exists());

        verifyNoInteractions(transferService);
    }

    @Test
    void blankDescriptionBecomesNull() throws Exception {
        when(transferService.create(eq(userId), any(CreateTransferRequest.class), anyString()))
            .thenReturn(created("1.00", null));

        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "1", today(), "\"  \"")))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateTransferRequest> captor = ArgumentCaptor.forClass(CreateTransferRequest.class);
        verify(transferService).create(eq(userId), captor.capture(), anyString());
        assertThat(captor.getValue().description()).isNull();
    }

    @Test
    void sameAccountOnBothSidesIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, fromId, "10", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Escolha contas diferentes"))
            .andExpect(jsonPath("$.errors.differentAccounts").value("Escolha contas diferentes"));

        verifyNoInteractions(transferService);
    }

    @Test
    void missingAccountsAre400OnTheFields() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(null, null, "10", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.fromAccountId").value("Escolha a conta de origem"))
            .andExpect(jsonPath("$.errors.toAccountId").value("Escolha a conta de destino"))
            .andExpect(jsonPath("$.errors.differentAccounts").doesNotExist());

        verifyNoInteractions(transferService);
    }

    @Test
    void zeroAmountIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "0", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.amount").value("O valor precisa ser maior que zero"));

        verifyNoInteractions(transferService);
    }

    @Test
    void negativeAmountIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "-5.00", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.amount").value("O valor precisa ser maior que zero"));
    }

    @Test
    void amountWithThreeDecimalsIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "10.123", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.amount")
                .value("Informe o valor com até 2 casas decimais e no máximo 13 dígitos antes da vírgula"));
    }

    @Test
    void amountOutsideDecimal15_2Is400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "12345678901234", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void missingAmountIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "null", today(), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.amount").value("Informe o valor da transferência"));
    }

    @Test
    void futureDateIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "10", today().plusDays(1), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.transferDate").value("A data não pode estar no futuro"));

        verifyNoInteractions(transferService);
    }

    @Test
    void missingDateIs400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "10", null, "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.transferDate").value("Informe a data da transferência"));
    }

    @Test
    void pastDateIsAccepted() throws Exception {
        when(transferService.create(eq(userId), any(CreateTransferRequest.class), anyString()))
            .thenReturn(created("10.00", null));

        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "10", today().minusDays(30), "null")))
            .andExpect(status().isCreated());
    }

    @Test
    void descriptionLongerThan200Is400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "10", today(), "\"" + "a".repeat(201) + "\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.description").value("Use no máximo 200 caracteres na descrição"));
    }

    @Test
    void accountNotFoundIs404() throws Exception {
        when(transferService.create(eq(userId), any(CreateTransferRequest.class), anyString()))
            .thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(post("/api/v1/transfers").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fromId, toId, "10", today(), "null")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }

    @Test
    void getListsTheJwtUsersTransfers() throws Exception {
        when(transferService.listRecent(userId)).thenReturn(List.of(response("200.00", null)));

        mockMvc.perform(get("/api/v1/transfers").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fromAccountName").value("Nubank"))
            .andExpect(jsonPath("$[0].toAccountName").value("Inter"))
            .andExpect(jsonPath("$[0].amount").value(200.0));
    }
}
