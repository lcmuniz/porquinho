package com.porquinho.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.dto.RegisterGoogleRequest;
import com.porquinho.entity.User;
import com.porquinho.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.porquinho.config.TestWebSecurityConfig;
import org.springframework.context.annotation.Import;

/**
 * Unit tests for AuthController.
 * Uses @WebMvcTest to test only the controller layer with mocked dependencies.
 * Security is disabled in test profile.
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void healthEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/auth/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Auth service is running"));
    }

    @Test
    void registerWithGoogleShouldReturnCreatedForNewUser() throws Exception {
        // Arrange
        RegisterGoogleRequest request = new RegisterGoogleRequest(
            "test@example.com",
            "google123",
            "Test User"
        );

        User newUser = new User("test@example.com", User.AuthProvider.GOOGLE, "google123");
        newUser.setId(UUID.randomUUID());

        AuthService.UserRegistrationResult result = new AuthService.UserRegistrationResult(newUser, true);

        when(authService.registerOrGetUserFromGoogle(anyString(), anyString(), anyString()))
            .thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void registerWithGoogleShouldReturnOkForExistingUser() throws Exception {
        // Arrange
        RegisterGoogleRequest request = new RegisterGoogleRequest(
            "existing@example.com",
            "google456",
            "Existing User"
        );

        User existingUser = new User("existing@example.com", User.AuthProvider.GOOGLE, "google456");
        existingUser.setId(UUID.randomUUID());

        AuthService.UserRegistrationResult result = new AuthService.UserRegistrationResult(existingUser, false);

        when(authService.registerOrGetUserFromGoogle(anyString(), anyString(), anyString()))
            .thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("existing@example.com"))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"));
    }

    @Test
    void registerWithGoogleShouldValidateRequest() throws Exception {
        // Arrange - invalid request (missing email)
        RegisterGoogleRequest invalidRequest = new RegisterGoogleRequest();
        invalidRequest.setGoogleId("google789");
        invalidRequest.setName("Invalid User");
        // email is missing

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithGoogleWorksWithoutAuthInTests() throws Exception {
        // Arrange
        RegisterGoogleRequest request = new RegisterGoogleRequest(
            "test@example.com",
            "google123",
            "Test User"
        );

        User newUser = new User("test@example.com", User.AuthProvider.GOOGLE, "google123");
        newUser.setId(UUID.randomUUID());
        AuthService.UserRegistrationResult result = new AuthService.UserRegistrationResult(newUser, true);

        when(authService.registerOrGetUserFromGoogle(anyString(), anyString(), anyString()))
            .thenReturn(result);

        // Act & Assert - In test profile, authentication is not required
        mockMvc.perform(post("/api/v1/auth/register/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
