package com.porquinho.controller;

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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Story 7.2: GET /api/v1/dashboard/health (JSON shape, user from the JWT, 401 without a token). */
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
}
