package com.porquinho.controller;

import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.DashboardLayer2Response;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stories 7.2, 7.3 and 7.5: GET /api/v1/dashboard/health, /layer1 and /layer2 (JSON shape, user from the JWT, 401 without a token). */
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
}
