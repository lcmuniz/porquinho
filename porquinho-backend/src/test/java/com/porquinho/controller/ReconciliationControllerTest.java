package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.AccountResponse;
import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.ReconciliationResponse;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.ReconciliationService;
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
import java.time.OffsetDateTime;
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

@WebMvcTest(ReconciliationController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class ReconciliationControllerTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-03T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    private UUID userId;
    private UUID accountId;
    private TestingAuthenticationToken authToken;

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

    private String url() {
        return "/api/v1/accounts/" + accountId + "/reconciliations";
    }

    private static String body(String bankBalance, String reason) {
        return "{\"bankBalance\":" + bankBalance + ",\"reason\":" + reason + "}";
    }

    private ReconciliationResponse event(String previous, String bank, String difference, String reason) {
        return new ReconciliationResponse(UUID.randomUUID(), accountId, new BigDecimal(previous), new BigDecimal(bank),
            new BigDecimal(difference), reason, AT);
    }

    private ReconciliationResponse.Created created(String bank, String difference) {
        AccountResponse account = new AccountResponse(accountId, "Nubank", null, "CHECKING", new BigDecimal("1000.00"),
            new BigDecimal(bank), AT.minusDays(30), AT);
        return new ReconciliationResponse.Created(account, event("1000.00", bank, difference, null));
    }

    @Test
    void postReconcilesForTheJwtUserAndReturns201WithAccountAndEvent() throws Exception {
        when(reconciliationService.reconcile(eq(userId), eq(accountId), any(CreateReconciliationRequest.class),
            anyString())).thenReturn(created("1050.00", "50.00"));

        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("1050.00", "\"  Tarifa estornada \"")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account.currentBalance").value(1050.0))
            .andExpect(jsonPath("$.account.lastReconciliationDate").value("2026-11-03T15:00:00Z"))
            .andExpect(jsonPath("$.reconciliation.previousBalance").value(1000.0))
            .andExpect(jsonPath("$.reconciliation.bankBalance").value(1050.0))
            .andExpect(jsonPath("$.reconciliation.difference").value(50.0));

        ArgumentCaptor<CreateReconciliationRequest> captor = ArgumentCaptor.forClass(CreateReconciliationRequest.class);
        verify(reconciliationService).reconcile(eq(userId), eq(accountId), captor.capture(), anyString());
        assertThat(captor.getValue().bankBalance()).isEqualByComparingTo("1050.00");
        assertThat(captor.getValue().reason()).isEqualTo("Tarifa estornada");
    }

    @Test
    void negativeBankBalanceIsAccepted() throws Exception {
        when(reconciliationService.reconcile(eq(userId), eq(accountId), any(CreateReconciliationRequest.class),
            anyString())).thenReturn(created("-30.00", "-1030.00"));

        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("-30.00", "null")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reconciliation.difference").value(-1030.0));
    }

    @Test
    void blankReasonBecomesNull() throws Exception {
        when(reconciliationService.reconcile(eq(userId), eq(accountId), any(CreateReconciliationRequest.class),
            anyString())).thenReturn(created("1000.00", "0.00"));

        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("1000", "\"   \"")))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateReconciliationRequest> captor = ArgumentCaptor.forClass(CreateReconciliationRequest.class);
        verify(reconciliationService).reconcile(eq(userId), eq(accountId), captor.capture(), anyString());
        assertThat(captor.getValue().reason()).isNull();
    }

    @Test
    void missingBankBalanceIs400() throws Exception {
        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.bankBalance").value("Informe o saldo no banco"));
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void bankBalanceWithThreeDecimalsIs400() throws Exception {
        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("10.123", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.bankBalance")
                .value("Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula"));
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void bankBalanceOutsideDecimal15_2Is400() throws Exception {
        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("12345678901234", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.bankBalance").exists());
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void reasonLongerThan200Is400() throws Exception {
        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("10", "\"" + "a".repeat(201) + "\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.reason").value("Use no máximo 200 caracteres no motivo"));
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void accountNotFoundIs404() throws Exception {
        when(reconciliationService.reconcile(eq(userId), eq(accountId), any(CreateReconciliationRequest.class),
            anyString())).thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("10", "null")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }

    @Test
    void differenceOutsideDecimal15_2Is400OnTheBankBalanceField() throws Exception {
        when(reconciliationService.reconcile(eq(userId), eq(accountId), any(CreateReconciliationRequest.class),
            anyString())).thenThrow(new BadRequestException(ReconciliationService.DIFFERENCE_OUT_OF_RANGE_MESSAGE,
            "bankBalance"));

        mockMvc.perform(post(url()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("9999999999999.99", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(ReconciliationService.DIFFERENCE_OUT_OF_RANGE_MESSAGE))
            .andExpect(jsonPath("$.errors.bankBalance").value(ReconciliationService.DIFFERENCE_OUT_OF_RANGE_MESSAGE));
    }

    @Test
    void getOfRemovedOrAnotherUsersAccountIs404() throws Exception {
        when(reconciliationService.listRecent(userId, accountId))
            .thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(get(url()).with(authentication(authToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }

    @Test
    void getListsTheAccountsReconciliations() throws Exception {
        when(reconciliationService.listRecent(userId, accountId))
            .thenReturn(List.of(event("1000.00", "980.00", "-20.00", "Tarifa")));

        mockMvc.perform(get(url()).with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].difference").value(-20.0))
            .andExpect(jsonPath("$[0].reason").value("Tarifa"))
            .andExpect(jsonPath("$[0].accountId").value(accountId.toString()));
    }
}
