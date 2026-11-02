package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.OnboardingProgress;
import com.porquinho.service.OnboardingService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnboardingController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OnboardingService onboardingService;

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

    private OnboardingProgress progress(int step, String... steps) {
        OnboardingProgress p = new OnboardingProgress(userId);
        p.setCurrentStep(step);
        for (String s : steps) {
            p.addStep(s);
        }
        return p;
    }

    @Test
    void getMeReturnsProgressOfTheJwtUser() throws Exception {
        when(onboardingService.getOrCreate(userId)).thenReturn(progress(1));

        mockMvc.perform(get("/api/v1/onboarding/me").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(1))
            .andExpect(jsonPath("$.stepsCompleted").isArray())
            .andExpect(jsonPath("$.stepsCompleted").isEmpty())
            .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void postTutorialViewedReturnsStepTwo() throws Exception {
        when(onboardingService.completeTutorial(eq(userId), eq(false), anyString()))
            .thenReturn(progress(2, "tutorial_viewed"));

        mockMvc.perform(post("/api/v1/onboarding/me/tutorial").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(2))
            .andExpect(jsonPath("$.stepsCompleted[0]").value("tutorial_viewed"))
            .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void postTutorialSkippedPassesSkippedToTheService() throws Exception {
        when(onboardingService.completeTutorial(eq(userId), eq(true), anyString()))
            .thenReturn(progress(2, "tutorial_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/tutorial").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stepsCompleted[0]").value("tutorial_skipped"));

        verify(onboardingService).completeTutorial(eq(userId), eq(true), anyString());
    }

    @Test
    void postTutorialWithoutBodyCountsAsViewed() throws Exception {
        when(onboardingService.completeTutorial(eq(userId), eq(false), anyString()))
            .thenReturn(progress(2, "tutorial_viewed"));

        mockMvc.perform(post("/api/v1/onboarding/me/tutorial").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(2));
    }
}
