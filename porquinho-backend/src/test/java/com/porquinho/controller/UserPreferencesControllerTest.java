package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.UserPreferences;
import com.porquinho.service.UserPreferencesService;
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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPreferencesController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class UserPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPreferencesService userPreferencesService;

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

    @Test
    void getReturnsThePreference() throws Exception {
        when(userPreferencesService.get(userId)).thenReturn(new UserPreferences(true));

        mockMvc.perform(get("/api/v1/users/me/preferences").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgetAlertEmail").value(true));
    }

    @Test
    void putUpdatesThePreference() throws Exception {
        when(userPreferencesService.update(userId, new UserPreferences(false))).thenReturn(new UserPreferences(false));

        mockMvc.perform(put("/api/v1/users/me/preferences").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"budgetAlertEmail\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgetAlertEmail").value(false));
    }

    @Test
    void putWithoutTheFieldIs400() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        verify(userPreferencesService, never()).update(any(), any());
    }
}
