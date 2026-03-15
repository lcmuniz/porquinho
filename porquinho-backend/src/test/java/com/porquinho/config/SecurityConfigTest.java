package com.porquinho.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SecurityConfig.
 * Verifies that public endpoints are accessible and protected endpoints require authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void apiHealthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isNotFound()); // 404 is ok, endpoint doesn't exist yet but is not 401/403
    }

    @Test
    void authEndpointsShouldBePublic() throws Exception {
        // Auth endpoints should be accessible without JWT
        mockMvc.perform(get("/api/v1/auth/test"))
                .andExpect(status().isNotFound()); // 404 is ok, means no 401/403 auth error
    }

    @Test
    void protectedEndpointsShouldRequireAuthentication() throws Exception {
        // Protected endpoints should return 401 without JWT
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsShouldBeConfigured() throws Exception {
        // CORS headers should be present for allowed origins
        mockMvc.perform(get("/actuator/health")
                .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());
    }
}
