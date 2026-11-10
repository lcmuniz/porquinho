package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.SaveBudgetRequest;
import com.porquinho.exception.BadRequestException;
import com.porquinho.service.BudgetService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Story 6.3: the budget endpoints (JSON shape, Bean Validation 400s, RFC 7807). */
@WebMvcTest(BudgetController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BudgetService budgetService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    @Test
    void getReturnsTheMonthInCamelCaseWithNulls() throws Exception {
        UUID category = UUID.randomUUID();
        when(budgetService.get(userId, "2026-11")).thenReturn(new BudgetResponse("2026-11", new BigDecimal("1500.00"),
            BigDecimal.ZERO, List.of(
                new BudgetResponse.CategoryBudget(category, null, new BigDecimal("30.00"), null, null, null))));

        mockMvc.perform(get("/api/v1/budgets/2026-11").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value("2026-11"))
            .andExpect(jsonPath("$.totalAvailable").value(1500.00))
            .andExpect(jsonPath("$.categories[0].categoryId").value(category.toString()))
            .andExpect(jsonPath("$.categories[0].allocated").isEmpty())
            .andExpect(jsonPath("$.categories[0].spent").value(30.00))
            .andExpect(jsonPath("$.categories[0].committed").isEmpty())
            .andExpect(jsonPath("$.categories[0].remaining").isEmpty())
            .andExpect(jsonPath("$.categories[0].status").isEmpty());
    }

    @Test
    void anInvalidMonthIs400() throws Exception {
        when(budgetService.get(userId, "2026-13"))
            .thenThrow(new BadRequestException(TransactionService.INVALID_MONTH_MESSAGE));

        mockMvc.perform(get("/api/v1/budgets/2026-13").with(authentication(authToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(TransactionService.INVALID_MONTH_MESSAGE));
    }

    @Test
    void aNegativeOrMissingValueIs400WithoutCallingTheService() throws Exception {
        String category = UUID.randomUUID().toString();
        for (String body : List.of(
            "{\"allocations\":[{\"categoryId\":\"" + category + "\",\"amount\":-1}]}",
            "{\"allocations\":[{\"categoryId\":\"" + category + "\"}]}",
            "{\"allocations\":[{\"amount\":10}]}",
            "{\"allocations\":[{\"categoryId\":\"" + category + "\",\"amount\":1.234}]}",
            "{}")) {
            mockMvc.perform(put("/api/v1/budgets/2026-11").with(authentication(authToken))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(budgetService);
    }

    @Test
    void putReturnsTheMonthRecalculated() throws Exception {
        UUID category = UUID.randomUUID();
        when(budgetService.save(any(), any(), any())).thenReturn(
            new BudgetResponse("2026-11", BigDecimal.TEN, BigDecimal.ONE, List.of()));

        mockMvc.perform(put("/api/v1/budgets/2026-11").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allocations\":[{\"categoryId\":\"" + category + "\",\"amount\":0}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAllocated").value(1));
        verify(budgetService).save(userId, "2026-11", List.of(new SaveBudgetRequest.Item(category, BigDecimal.ZERO)));
    }
}
