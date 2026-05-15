package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.AccountDeletionResponse;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.AccountDeletionService;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountDeletionController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AccountDeletionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountDeletionService accountDeletionService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    @Test
    void requestDeletionShouldReturn201WithBody() throws Exception {
        AccountDeletionResponse response = new AccountDeletionResponse(
                UUID.randomUUID(),
                DeletionStatus.PENDING_PURGE,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(30));

        when(accountDeletionService.requestDeletion(eq(userId), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/account-deletion")
                .with(authentication(authToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PURGE"))
                .andExpect(jsonPath("$.scheduledPurgeAt").isNotEmpty());
    }

    @Test
    void requestDeletionShouldReturn429WhenRateLimited() throws Exception {
        when(accountDeletionService.requestDeletion(eq(userId), anyString()))
                .thenThrow(new RateLimitExceededException("Rate limit exceeded"));

        mockMvc.perform(post("/api/v1/account-deletion")
                .with(authentication(authToken)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requestDeletionShouldReturn404WhenUserNotFound() throws Exception {
        when(accountDeletionService.requestDeletion(eq(userId), anyString()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(post("/api/v1/account-deletion")
                .with(authentication(authToken)))
                .andExpect(status().isNotFound());
    }
}
