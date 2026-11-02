package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.OnboardingProgress;
import com.porquinho.exception.ConflictException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    // ---- Story 3.2 ----

    @Test
    void postAccountsReturnsStepThree() throws Exception {
        when(onboardingService.completeAccounts(eq(userId), eq(false), anyString()))
            .thenReturn(progress(3, "tutorial_viewed", "accounts_added"));

        mockMvc.perform(post("/api/v1/onboarding/me/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(3))
            .andExpect(jsonPath("$.stepsCompleted[1]").value("accounts_added"));
    }

    @Test
    void postAccountsSkippedPassesSkippedToTheService() throws Exception {
        when(onboardingService.completeAccounts(eq(userId), eq(true), anyString()))
            .thenReturn(progress(3, "tutorial_viewed", "accounts_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stepsCompleted[1]").value("accounts_skipped"));

        verify(onboardingService).completeAccounts(eq(userId), eq(true), anyString());
    }

    @Test
    void postAccountsWithoutAccountsIs409() throws Exception {
        when(onboardingService.completeAccounts(eq(userId), eq(false), anyString()))
            .thenThrow(new ConflictException("Adicione ao menos uma conta para continuar"));

        mockMvc.perform(post("/api/v1/onboarding/me/accounts").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Adicione ao menos uma conta para continuar"));
    }

    @Test
    void postCardsReturnsStepFour() throws Exception {
        when(onboardingService.completeCards(eq(userId), eq(false), anyString()))
            .thenReturn(progress(4, "tutorial_viewed", "accounts_added", "cards_added"));

        mockMvc.perform(post("/api/v1/onboarding/me/cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(4))
            .andExpect(jsonPath("$.stepsCompleted[2]").value("cards_added"));
    }

    @Test
    void postCardsSkippedPassesSkippedToTheService() throws Exception {
        when(onboardingService.completeCards(eq(userId), eq(true), anyString()))
            .thenReturn(progress(4, "tutorial_viewed", "accounts_added", "cards_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stepsCompleted[2]").value("cards_skipped"));

        verify(onboardingService).completeCards(eq(userId), eq(true), anyString());
    }

    @Test
    void postCardsWithoutBodyCountsAsNotSkipped() throws Exception {
        when(onboardingService.completeCards(eq(userId), eq(false), anyString()))
            .thenReturn(progress(4, "tutorial_viewed", "accounts_added", "cards_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/cards").with(authentication(authToken)))
            .andExpect(status().isOk());

        verify(onboardingService).completeCards(eq(userId), eq(false), anyString());
    }

    @Test
    void postCardsBeforeStepThreeIs409() throws Exception {
        when(onboardingService.completeCards(eq(userId), eq(false), anyString()))
            .thenThrow(new ConflictException("Conclua o tutorial e as contas antes de adicionar seus cartões."));

        mockMvc.perform(post("/api/v1/onboarding/me/cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isConflict());
    }

    // ---- Story 3.4: step 4, suggested categories ----

    @Test
    void postCategoriesPassesTheKeysAndReturnsStepFive() throws Exception {
        when(onboardingService.completeCategories(eq(userId), eq(List.of("housing", "leisure")), anyString()))
            .thenReturn(progress(5, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested"));

        mockMvc.perform(post("/api/v1/onboarding/me/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[\"housing\",\"leisure\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(5))
            .andExpect(jsonPath("$.stepsCompleted[3]").value("categories_suggested"));
    }

    @Test
    void postCategoriesWithNoKeyIs400() throws Exception {
        for (String body : new String[] {"{\"keys\":[]}", "{}"}) {
            mockMvc.perform(post("/api/v1/onboarding/me/categories").with(authentication(authToken))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Escolha ao menos uma categoria"));
        }
        mockMvc.perform(post("/api/v1/onboarding/me/categories").with(authentication(authToken)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(onboardingService);
    }

    @Test
    void postCategoriesWithAnUnknownKeyIs400() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/me/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[\"housing\",\"xyz\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Categoria sugerida desconhecida. Recarregue a página e tente novamente."));
        verifyNoInteractions(onboardingService);
    }

    @Test
    void postCategoriesWithMoreThan50KeysIs400() throws Exception {
        String keys = String.join(",", java.util.Collections.nCopies(51, "\"housing\""));
        mockMvc.perform(post("/api/v1/onboarding/me/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[" + keys + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Envie no máximo 50 categorias de uma vez"));
        verifyNoInteractions(onboardingService);
    }

    @Test
    void postCategoriesBeforeStepFourIs409() throws Exception {
        when(onboardingService.completeCategories(eq(userId), any(), anyString()))
            .thenThrow(new ConflictException("Conclua os passos anteriores antes de escolher suas categorias."));

        mockMvc.perform(post("/api/v1/onboarding/me/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[\"housing\"]}"))
            .andExpect(status().isConflict());
    }

    // ---- Story 3.5: step 5, customizing the categories ----

    @Test
    void postCustomizationReturnsStepSix() throws Exception {
        when(onboardingService.completeCustomization(eq(userId), eq(false), anyString()))
            .thenReturn(progress(6, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested",
                "categories_customized"));

        mockMvc.perform(post("/api/v1/onboarding/me/customization").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(6))
            .andExpect(jsonPath("$.stepsCompleted[4]").value("categories_customized"));
    }

    @Test
    void postCustomizationSkippedPassesSkippedAndNoBodyMeansNotSkipped() throws Exception {
        when(onboardingService.completeCustomization(eq(userId), any(Boolean.class), anyString()))
            .thenReturn(progress(6, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested",
                "customization_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/customization").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":true}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/onboarding/me/customization").with(authentication(authToken)))
            .andExpect(status().isOk());

        verify(onboardingService).completeCustomization(eq(userId), eq(true), anyString());
        verify(onboardingService).completeCustomization(eq(userId), eq(false), anyString());
    }

    @Test
    void postCustomizationBeforeStepFiveIs409() throws Exception {
        when(onboardingService.completeCustomization(eq(userId), eq(false), anyString()))
            .thenThrow(new ConflictException("Conclua os passos anteriores antes de personalizar suas categorias."));

        mockMvc.perform(post("/api/v1/onboarding/me/customization").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isConflict());
    }

    // ---- Story 3.6: step 6, first import (no upload yet) ----

    @Test
    void postImportWithoutBodyReturnsStepSeven() throws Exception {
        when(onboardingService.completeImport(eq(userId), anyString()))
            .thenReturn(progress(7, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested",
                "categories_customized", "import_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/import").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(7))
            .andExpect(jsonPath("$.stepsCompleted[5]").value("import_skipped"));

        verify(onboardingService).completeImport(eq(userId), anyString());
    }

    @Test
    void postImportBeforeStepSixIs409() throws Exception {
        when(onboardingService.completeImport(eq(userId), anyString()))
            .thenThrow(new ConflictException("Conclua os passos anteriores antes de importar suas transações."));

        mockMvc.perform(post("/api/v1/onboarding/me/import").with(authentication(authToken)))
            .andExpect(status().isConflict());
    }

    // ---- Story 3.7: step 7, Dashboard GPS tour ----

    @Test
    void postDashboardTourWithoutBodyReturnsStepEight() throws Exception {
        when(onboardingService.completeDashboardTour(eq(userId), anyString()))
            .thenReturn(progress(8, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested",
                "categories_customized", "import_skipped", "dashboard_gps_explained"));

        mockMvc.perform(post("/api/v1/onboarding/me/dashboard-tour").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(8))
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.stepsCompleted[6]").value("dashboard_gps_explained"));

        verify(onboardingService).completeDashboardTour(eq(userId), anyString());
    }

    @Test
    void postDashboardTourFromStepEightReturns200WithUnchangedProgress() throws Exception {
        // Idempotent: the service returns the current progress as is (no change, no audit, covered in the
        // service tests); the endpoint just answers 200 with it.
        when(onboardingService.completeDashboardTour(eq(userId), anyString()))
            .thenReturn(progress(8, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested",
                "categories_customized", "import_skipped", "dashboard_gps_explained"));

        mockMvc.perform(post("/api/v1/onboarding/me/dashboard-tour").with(authentication(authToken)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/onboarding/me/dashboard-tour").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(8))
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.stepsCompleted.length()").value(7))
            .andExpect(jsonPath("$.stepsCompleted[6]").value("dashboard_gps_explained"));

        verify(onboardingService, times(2)).completeDashboardTour(eq(userId), anyString());
    }

    @Test
    void postDashboardTourBeforeStepSevenIs409() throws Exception {
        when(onboardingService.completeDashboardTour(eq(userId), anyString()))
            .thenThrow(new ConflictException("Conclua os passos anteriores antes de conhecer o Dashboard GPS."));

        mockMvc.perform(post("/api/v1/onboarding/me/dashboard-tour").with(authentication(authToken)))
            .andExpect(status().isConflict());
    }

    // ---- Story 3.8: step 8, emergency fund goal (completes the onboarding) ----

    private OnboardingProgress completedProgress(String lastStep) {
        OnboardingProgress p = progress(8, "tutorial_viewed", "accounts_added", "cards_skipped", "categories_suggested",
            "categories_customized", "import_skipped", "dashboard_gps_explained", lastStep);
        p.setCompleted(true);
        return p;
    }

    @Test
    void postFinishWithoutSkippingCompletesTheOnboarding() throws Exception {
        when(onboardingService.finish(eq(userId), eq(false), anyString()))
            .thenReturn(completedProgress("emergency_fund_created"));

        mockMvc.perform(post("/api/v1/onboarding/me/finish").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value(8))
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.stepsCompleted[7]").value("emergency_fund_created"));

        verify(onboardingService).finish(eq(userId), eq(false), anyString());
    }

    @Test
    void postFinishSkippedPassesSkippedTrue() throws Exception {
        when(onboardingService.finish(eq(userId), eq(true), anyString()))
            .thenReturn(completedProgress("emergency_fund_skipped"));

        mockMvc.perform(post("/api/v1/onboarding/me/finish").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.stepsCompleted[7]").value("emergency_fund_skipped"));
    }

    @Test
    void postFinishWithoutBodyMeansNotSkipped() throws Exception {
        when(onboardingService.finish(eq(userId), eq(false), anyString()))
            .thenReturn(completedProgress("emergency_fund_created"));

        mockMvc.perform(post("/api/v1/onboarding/me/finish").with(authentication(authToken)))
            .andExpect(status().isOk());

        verify(onboardingService).finish(eq(userId), eq(false), anyString());
    }

    @Test
    void postFinishWithoutGoalOrBeforeStepEightIs409() throws Exception {
        when(onboardingService.finish(eq(userId), eq(false), anyString()))
            .thenThrow(new ConflictException("Crie sua meta para concluir, ou toque em \"Pular por agora\"."));

        mockMvc.perform(post("/api/v1/onboarding/me/finish").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"skipped\":false}"))
            .andExpect(status().isConflict());
    }
}
