package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.BalanceHistoryResponse;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.BalanceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BalanceHistoryController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class BalanceHistoryControllerTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-03T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BalanceHistoryService balanceHistoryService;

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
        return "/api/v1/accounts/" + accountId + "/balance-history";
    }

    private BalanceHistoryResponse response() {
        return new BalanceHistoryResponse(
            new BalanceHistoryResponse.AccountSummary(accountId, "Nubank", new BigDecimal("1050.00")),
            AT.minusDays(30).toInstant(),
            List.of(new BalanceHistoryResponse.Point(AT.minusDays(1), new BigDecimal("1000.00"), "initial"),
                new BalanceHistoryResponse.Point(AT, new BigDecimal("-50.00"), "reconciliation")));
    }

    @Test
    void getReturnsTheAccountAndItsPointsForTheJwtUser() throws Exception {
        when(balanceHistoryService.history(userId, accountId, "30d")).thenReturn(response());

        mockMvc.perform(get(url()).param("range", "30d").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.id").value(accountId.toString()))
            .andExpect(jsonPath("$.account.accountName").value("Nubank"))
            .andExpect(jsonPath("$.account.currentBalance").value(1050.0))
            .andExpect(jsonPath("$.periodStart").value("2026-10-04T15:00:00Z"))
            .andExpect(jsonPath("$.points.length()").value(2))
            .andExpect(jsonPath("$.points[0].event").value("initial"))
            .andExpect(jsonPath("$.points[1].balance").value(-50.0))
            .andExpect(jsonPath("$.points[1].date").value("2026-11-03T15:00:00Z"));
    }

    @Test
    void rangeDefaultsToThreeMonths() throws Exception {
        when(balanceHistoryService.history(userId, accountId, "3m")).thenReturn(response());

        mockMvc.perform(get(url()).with(authentication(authToken)))
            .andExpect(status().isOk());
        verify(balanceHistoryService).history(userId, accountId, "3m");
    }

    @Test
    void invalidRangeIs400() throws Exception {
        when(balanceHistoryService.history(userId, accountId, "abc"))
            .thenThrow(new BadRequestException(BalanceHistoryService.INVALID_RANGE_MESSAGE, "range"));

        mockMvc.perform(get(url()).param("range", "abc").with(authentication(authToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Período inválido"));
    }

    @Test
    void anotherUsersAccountIs404() throws Exception {
        when(balanceHistoryService.history(userId, accountId, "3m"))
            .thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(get(url()).with(authentication(authToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Conta não encontrada"));
    }
}
