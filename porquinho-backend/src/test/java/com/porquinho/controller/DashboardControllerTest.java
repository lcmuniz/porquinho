package com.porquinho.controller;

import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.DashboardLayer2Response;
import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.exception.BadRequestException;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stories 7.2, 7.3, 7.5 and 7.6: GET /api/v1/dashboard/health, /layer1, /layer2 and /layer3 (JSON shape, user from the JWT, 401 without a token). */
@WebMvcTest(DashboardController.class)
@ActiveProfiles("test")
@Import(DashboardControllerTest.ApiRequiresAuthentication.class)
class DashboardControllerTest {

    /** Same rule as SecurityConfig for /api/v1/** (the shared test config permits everything). */
    @TestConfiguration
    @EnableWebSecurity
    static class ApiRequiresAuthentication {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void returnsTheHealthOfTheJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(dashboardService.health(userId))
            .thenReturn(new FinancialHealthResponse(HealthStatus.ATTENTION, 9, 0, 1, 0, 2));

        mockMvc.perform(get("/api/v1/dashboard/health")
                .with(authentication(new TestingAuthenticationToken(jwt, null, "ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ATTENTION"))
            .andExpect(jsonPath("$.healthyCategories").value(9))
            .andExpect(jsonPath("$.lowCategories").value(0))
            .andExpect(jsonPath("$.overspentCategories").value(1))
            .andExpect(jsonPath("$.negativeAccounts").value(0))
            .andExpect(jsonPath("$.goalsBehind").value(2));
    }

    @Test
    void withoutATokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/health"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void returnsTheOverviewOfTheJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(dashboardService.overview(userId)).thenReturn(new DashboardLayer1Response(new BigDecimal("1500.00"),
            List.of(new DashboardLayer1Response.CategoryStatus(categoryId, "Lazer", BudgetStatus.OVERSPENT,
                new BigDecimal("-20.00"))),
            new DashboardLayer1Response.MainGoal("Reserva", new BigDecimal("250.00"), new BigDecimal("1000.00"), 25),
            new BigDecimal("320.00"), new BigDecimal("1000.00"), new BigDecimal("800.00"),
            List.of(new DashboardLayer1Response.WealthPoint(LocalDate.of(2025, 11, 12), new BigDecimal("1000.00")),
                new DashboardLayer1Response.WealthPoint(LocalDate.of(2026, 11, 12), new BigDecimal("1500.00")))));

        mockMvc.perform(get("/api/v1/dashboard/layer1")
                .with(authentication(new TestingAuthenticationToken(jwt, null, "ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBalance").value(1500.00))
            .andExpect(jsonPath("$.categories[0].categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.categories[0].name").value("Lazer"))
            .andExpect(jsonPath("$.categories[0].status").value("OVERSPENT"))
            .andExpect(jsonPath("$.categories[0].remaining").value(-20.00))
            .andExpect(jsonPath("$.mainGoal.name").value("Reserva"))
            .andExpect(jsonPath("$.mainGoal.currentAmount").value(250.00))
            .andExpect(jsonPath("$.mainGoal.targetAmount").value(1000.00))
            .andExpect(jsonPath("$.mainGoal.percent").value(25))
            .andExpect(jsonPath("$.monthSpent").value(320.00))
            .andExpect(jsonPath("$.monthAllocated").value(1000.00))
            .andExpect(jsonPath("$.projectedBalance").value(800.00))
            .andExpect(jsonPath("$.wealthHistory[0].date").value("2025-11-12"))
            .andExpect(jsonPath("$.wealthHistory[0].balance").value(1000.00))
            .andExpect(jsonPath("$.wealthHistory[1].date").value("2026-11-12"))
            .andExpect(jsonPath("$.wealthHistory[1].balance").value(1500.00));
    }

    @Test
    void overviewWithoutATokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/layer1"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void returnsTheTacticalViewOfTheJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(dashboardService.tactical(userId)).thenReturn(new DashboardLayer2Response("2026-11",
            List.of(new DashboardLayer2Response.CategorySpending(categoryId, "Lazer", "#EC4899",
                new BigDecimal("620.00"), new BigDecimal("500.00"), BudgetStatus.OVERSPENT),
                new DashboardLayer2Response.CategorySpending(null, "Não classificado", null, new BigDecimal("40.00"),
                    null, null)),
            List.of(new DashboardLayer2Response.CategoryTrend(categoryId, "Lazer",
                List.of(new DashboardLayer2Response.MonthSpent("2026-11", new BigDecimal("620.00")))))));

        mockMvc.perform(get("/api/v1/dashboard/layer2")
                .with(authentication(new TestingAuthenticationToken(jwt, null, "ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value("2026-11"))
            .andExpect(jsonPath("$.categories[0].categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.categories[0].name").value("Lazer"))
            .andExpect(jsonPath("$.categories[0].colorHex").value("#EC4899"))
            .andExpect(jsonPath("$.categories[0].spent").value(620.00))
            .andExpect(jsonPath("$.categories[0].allocated").value(500.00))
            .andExpect(jsonPath("$.categories[0].status").value("OVERSPENT"))
            .andExpect(jsonPath("$.categories[1].categoryId").doesNotExist())
            .andExpect(jsonPath("$.categories[1].name").value("Não classificado"))
            .andExpect(jsonPath("$.trends[0].categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.trends[0].months[0].month").value("2026-11"))
            .andExpect(jsonPath("$.trends[0].months[0].spent").value(620.00));
    }

    @Test
    void tacticalWithoutATokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/layer2"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }

    private static Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("test-token").header("alg", "ES256").subject(userId.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    @Test
    void layer3PassesTheFiltersOfTheJwtUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID card = UUID.randomUUID();
        DashboardService.DetailsFilter expected = new DashboardService.DetailsFilter("2026-11-01", "2026-11-30",
            List.of(category), true, List.of(account), List.of(card), "mercado", "amount,desc");
        when(dashboardService.details(userId, expected, 2)).thenReturn(new DashboardLayer3Response(List.of(), 2, 120, 3));

        mockMvc.perform(get("/api/v1/dashboard/layer3").param("from", "2026-11-01").param("to", "2026-11-30")
                .param("categoryIds", category.toString()).param("uncategorized", "true")
                .param("accountIds", account.toString()).param("creditCardIds", card.toString())
                .param("q", "mercado").param("sort", "amount,desc").param("page", "2")
                .with(authentication(new TestingAuthenticationToken(jwt(userId), null, "ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.totalItems").value(120))
            .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void layer3InvalidPeriodIs400() throws Exception {
        UUID userId = UUID.randomUUID();
        when(dashboardService.details(eq(userId), any(), eq(0))).thenThrow(new BadRequestException("Período inválido"));

        mockMvc.perform(get("/api/v1/dashboard/layer3").param("from", "x")
                .with(authentication(new TestingAuthenticationToken(jwt(userId), null, "ROLE_USER"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void layer3ExportIsACsvAttachment() throws Exception {
        UUID userId = UUID.randomUUID();
        when(dashboardService.detailsCsv(eq(userId), any())).thenReturn("\uFEFFData;Descrição\r\n");

        mockMvc.perform(get("/api/v1/dashboard/layer3/export").param("sort", "category,asc")
                .with(authentication(new TestingAuthenticationToken(jwt(userId), null, "ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"transacoes.csv\""))
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string("\uFEFFData;Descrição\r\n"));
        verify(dashboardService).detailsCsv(userId, new DashboardService.DetailsFilter(null, null, null, false, null,
            null, null, "category,asc"));
    }

    @Test
    void layer3ExportPassesEveryFilter() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID card = UUID.randomUUID();
        when(dashboardService.detailsCsv(eq(userId), any())).thenReturn("x");

        mockMvc.perform(get("/api/v1/dashboard/layer3/export").param("from", "2026-10-01").param("to", "2026-10-31")
                .param("categoryIds", category.toString()).param("uncategorized", "true")
                .param("accountIds", account.toString()).param("creditCardIds", card.toString())
                .param("q", "mercado").param("sort", "date,asc")
                .with(authentication(new TestingAuthenticationToken(jwt(userId), null, "ROLE_USER"))))
            .andExpect(status().isOk());
        verify(dashboardService).detailsCsv(userId, new DashboardService.DetailsFilter("2026-10-01", "2026-10-31",
            List.of(category), true, List.of(account), List.of(card), "mercado", "date,asc"));
    }

    @Test
    void layer3ExportInvalidPeriodIs400() throws Exception {
        UUID userId = UUID.randomUUID();
        when(dashboardService.detailsCsv(eq(userId), any())).thenThrow(new BadRequestException("Período inválido"));

        mockMvc.perform(get("/api/v1/dashboard/layer3/export")
                .with(authentication(new TestingAuthenticationToken(jwt(userId), null, "ROLE_USER"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void layer3WithoutATokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/layer3")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/layer3/export")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }
}
