package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.AccountService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    private Account account(String name, String balance) {
        Account a = new Account(userId, name, "Nubank", Account.AccountType.CHECKING, new BigDecimal(balance));
        a.setId(UUID.randomUUID());
        a.setCreatedAt(OffsetDateTime.parse("2026-11-02T12:00:00Z"));
        return a;
    }

    private String body(String name, String bank, String type, String balance) {
        return "{\"accountName\":" + name + ",\"bankName\":" + bank + ",\"accountType\":" + type
            + ",\"initialBalance\":" + balance + "}";
    }

    @Test
    void postCreatesTheAccountForTheJwtUserAndReturns201() throws Exception {
        when(accountService.create(eq(userId), any(CreateAccountRequest.class))).thenReturn(account("Nubank", "1234.56"));

        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\" Nubank \"", "\"Nubank\"", "\"CHECKING\"", "1234.56")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountName").value("Nubank"))
            .andExpect(jsonPath("$.accountType").value("CHECKING"))
            .andExpect(jsonPath("$.initialBalance").value(1234.56))
            .andExpect(jsonPath("$.currentBalance").value(1234.56));

        ArgumentCaptor<CreateAccountRequest> captor = ArgumentCaptor.forClass(CreateAccountRequest.class);
        verify(accountService).create(eq(userId), captor.capture());
        assertThat(captor.getValue().accountName()).isEqualTo("Nubank");
    }

    @Test
    void negativeBalanceIsAccepted() throws Exception {
        when(accountService.create(eq(userId), any(CreateAccountRequest.class))).thenReturn(account("Itaú", "-150.00"));

        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Itaú\"", "null", "\"CHECKING\"", "-150.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.initialBalance").value(-150.0));
    }

    @Test
    void blankNameIs400WithThePtBrMessageOnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"   \"", "null", "\"CHECKING\"", "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Informe o nome da conta"))
            .andExpect(jsonPath("$.errors.accountName").value("Informe o nome da conta"));

        verifyNoInteractions(accountService);
    }

    @Test
    void nameMadeOfNonBreakingSpacesIs400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"\u00a0\u00a0\u2007\"", "null", "\"CHECKING\"", "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.accountName").value("Informe o nome da conta"));

        verifyNoInteractions(accountService);
    }

    @Test
    void nameLongerThan100Is400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"" + "a".repeat(101) + "\"", "null", "\"CHECKING\"", "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.accountName").exists());
    }

    @Test
    void bankLongerThan100Is400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Conta\"", "\"" + "b".repeat(101) + "\"", "\"CHECKING\"", "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.bankName").exists());
    }

    @Test
    void balanceWithThreeDecimalsIs400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Conta\"", "null", "\"CHECKING\"", "10.123")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.initialBalance").exists());
    }

    @Test
    void balanceOutsideDecimal15_2Is400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Conta\"", "null", "\"CHECKING\"", "10000000000000.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.initialBalance").exists());
    }

    @Test
    void missingBalanceAndInvalidTypeAre400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Conta\"", "null", "\"CREDIT\"", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.initialBalance").value("Informe o saldo inicial (use 0,00 se não souber)"))
            .andExpect(jsonPath("$.errors.accountType").value("Escolha o tipo da conta: corrente ou poupança"));
    }

    @Test
    void nonNumericBalanceIs400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Conta\"", "null", "\"CHECKING\"", "\"abc\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Dados inválidos. Confira os campos e tente novamente."));
    }

    @Test
    void getListsTheJwtUsersAccounts() throws Exception {
        when(accountService.listActive(userId)).thenReturn(List.of(account("Nubank", "10.00"), account("Itaú", "-5.50")));

        mockMvc.perform(get("/api/v1/accounts").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].accountName").value("Nubank"))
            .andExpect(jsonPath("$[1].currentBalance").value(-5.5));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/accounts/" + id).with(authentication(authToken)))
            .andExpect(status().isNoContent());

        verify(accountService).delete(userId, id);
    }

    @Test
    void deleteOfAnotherUsersAccountIs404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Conta não encontrada")).when(accountService).delete(userId, id);

        mockMvc.perform(delete("/api/v1/accounts/" + id).with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }
}
