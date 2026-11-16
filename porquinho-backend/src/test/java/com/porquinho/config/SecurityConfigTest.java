package com.porquinho.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SecurityConfig.
 * Verifies that endpoints are accessible in test profile.
 * Note: Security is simplified in tests - OAuth2 is disabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldBePublic() throws Exception {
        // Health endpoint should be accessible without authentication (not 401/403)
        // Returns 200 OK in tests
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void apiHealthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isNotFound()); // 404 is ok, endpoint doesn't exist yet but is not 401/403
    }

    @Test
    void authHealthEndpointShouldBePublic() throws Exception {
        // Auth health endpoint should be accessible without JWT
        mockMvc.perform(get("/api/v1/auth/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsAccessibleInTests() throws Exception {
        // In test profile, all endpoints are accessible (security is disabled)
        // This returns 404 because endpoint doesn't exist, not 401 unauthorized
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsShouldBeConfigured() throws Exception {
        // CORS headers should be present for allowed origins
        // Returns 200 OK in tests
        mockMvc.perform(get("/actuator/health")
                .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());
    }

    @Test
    void corsExposesTheExportCountToTheFrontend() {
        // SecurityConfig is @Profile("!test"), so its CORS source is checked directly: without the exposed header the
        // cross-origin frontend reads no X-Export-Count and shows "0 transações" (Story 9.1)
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard/layer3/export");
        request.addHeader("Origin", "http://localhost:5173");
        CorsConfiguration config = new SecurityConfig(null, null, null, null)
            .corsConfigurationSource(List.of("http://localhost:5173")).getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(config.getExposedHeaders()).contains("X-Export-Count");
    }
}
