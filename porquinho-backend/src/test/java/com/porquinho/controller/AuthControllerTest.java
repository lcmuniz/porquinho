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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 * Uses @WebMvcTest to test only the controller layer with mocked dependencies.
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
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
    @WithMockUser
    void registerWithGoogleShouldReturnCreatedForNewUser() throws Exception {
        // Arrange
        RegisterGoogleRequest request = new RegisterGoogleRequest(
            "test@example.com",
            "google123",
            "Test User"
        );

        LocalDateTime now = LocalDateTime.now();
        User newUser = new User("test@example.com", User.AuthProvider.GOOGLE, "google123");
        newUser.setId(UUID.randomUUID());
        // Simulate new user: createdAt equals updatedAt
        newUser.setCreatedAt(now);
        newUser.setUpdatedAt(now);

        when(authService.registerOrGetUserFromGoogle(anyString(), anyString()))
            .thenReturn(newUser);

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
    @WithMockUser
    void registerWithGoogleShouldReturnOkForExistingUser() throws Exception {
        // Arrange
        RegisterGoogleRequest request = new RegisterGoogleRequest(
            "existing@example.com",
            "google456",
            "Existing User"
        );

        LocalDateTime pastTime = LocalDateTime.now().minusDays(1);
        LocalDateTime now = LocalDateTime.now();
        User existingUser = new User("existing@example.com", User.AuthProvider.GOOGLE, "google456");
        existingUser.setId(UUID.randomUUID());
        // Simulate existing user: createdAt != updatedAt
        existingUser.setCreatedAt(pastTime);
        existingUser.setUpdatedAt(now);

        when(authService.registerOrGetUserFromGoogle(anyString(), anyString()))
            .thenReturn(existingUser);

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
    @WithMockUser
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
    void registerWithGoogleShouldRequireAuthentication() throws Exception {
        // Arrange
        RegisterGoogleRequest request = new RegisterGoogleRequest(
            "test@example.com",
            "google123",
            "Test User"
        );

        // Act & Assert - no authentication, should fail
        mockMvc.perform(post("/api/v1/auth/register/google")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
