package com.porquinho.controller;

import com.porquinho.dto.CompleteCategoriesRequest;
import com.porquinho.dto.CompleteOnboardingImportRequest;
import com.porquinho.dto.OnboardingProgressResponse;
import com.porquinho.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Guided onboarding endpoints (Story 3.1: tutorial; Story 3.2: bank accounts; Story 3.3: credit cards; Story 3.4: categories;
 * Story 3.5: customizing the categories; Story 3.6: first import, without upload; Story 3.7: Dashboard GPS tour; Story 3.8: emergency fund goal
 * and completion).
 * The user always comes from the JWT.
 * Not exempt from SubscriptionAccessFilter: the trial is already active during onboarding.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /** Current progress; creates the row at step 1 on the first access. */
    @GetMapping("/me")
    public ResponseEntity<OnboardingProgressResponse> getMyProgress(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(OnboardingProgressResponse.of(onboardingService.getOrCreate(userId)));
    }

    /**
     * Step 1 done (tutorial viewed, or skipped after confirmation). Moves to step 2.
     * Idempotent: repeating returns 200 with the current state. Missing body means "viewed".
     */
    @PostMapping("/me/tutorial")
    public ResponseEntity<OnboardingProgressResponse> completeTutorial(@AuthenticationPrincipal Jwt jwt,
                                                                       @RequestBody(required = false) StepRequest body,
                                                                       HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeTutorial(userId, skipped, request.getRemoteAddr())));
    }

    /**
     * Step 2 done (Story 3.2). "Próximo" needs at least 1 active account (409 otherwise); skipping with no
     * account creates "Minha Conta Principal". Moves to step 3. 409 while on step 1; idempotent from step 3 on.
     * Missing body means "not skipped".
     */
    @PostMapping("/me/accounts")
    public ResponseEntity<OnboardingProgressResponse> completeAccounts(@AuthenticationPrincipal Jwt jwt,
                                                                       @RequestBody(required = false) StepRequest body,
                                                                       HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeAccounts(userId, skipped, request.getRemoteAddr())));
    }

    /**
     * Step 3 done (Story 3.3). Cards are optional: "Próximo" with no card records cards_skipped, like "Pular".
     * Moves to step 4. 409 while on step 1 or 2; idempotent from step 4 on. Missing body means "not skipped".
     */
    @PostMapping("/me/cards")
    public ResponseEntity<OnboardingProgressResponse> completeCards(@AuthenticationPrincipal Jwt jwt,
                                                                    @RequestBody(required = false) StepRequest body,
                                                                    HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeCards(userId, skipped, request.getRemoteAddr())));
    }

    /**
     * Step 4 done (Story 3.4): creates the chosen suggested categories and moves to step 5. 400 with no key or
     * an unknown key; 409 while on steps 1 to 3; idempotent from step 5 on (200, nothing created).
     */
    @PostMapping("/me/categories")
    public ResponseEntity<OnboardingProgressResponse> completeCategories(@AuthenticationPrincipal Jwt jwt,
                                                                         @Valid @RequestBody CompleteCategoriesRequest body,
                                                                         HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeCategories(userId, body.keys(), request.getRemoteAddr())));
    }

    /**
     * Step 5 done (Story 3.5): records categories_customized (or customization_skipped) and moves to step 6. The
     * edits themselves were saved through /api/v1/categories. 409 while on steps 1 to 4; idempotent from step 6 on.
     * Missing body means "not skipped".
     */
    @PostMapping("/me/customization")
    public ResponseEntity<OnboardingProgressResponse> completeCustomization(@AuthenticationPrincipal Jwt jwt,
                                                                            @RequestBody(required = false) StepRequest body,
                                                                            HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeCustomization(userId, skipped, request.getRemoteAddr())));
    }

    /**
     * Step 6 done, moving to step 7. Without body, "Importar depois" records import_skipped (Story 3.6); with
     * {importId}, a finished import records first_import_completed (Story 5.10): 404 if not the user's, 409 in any other
     * status. 409 while on steps 1 to 5; idempotent from step 7 on.
     */
    @PostMapping("/me/import")
    public ResponseEntity<OnboardingProgressResponse> completeImport(@AuthenticationPrincipal Jwt jwt,
                                                                     @RequestBody(required = false) CompleteOnboardingImportRequest body,
                                                                     HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID importId = body == null ? null : body.importId();
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeImport(userId, importId, request.getRemoteAddr())));
    }

    /**
     * Step 7 done (Story 3.7): the Dashboard GPS tour records dashboard_gps_explained and moves to step 8.
     * No body. 409 while on steps 1 to 6; idempotent from step 8 on.
     */
    @PostMapping("/me/dashboard-tour")
    public ResponseEntity<OnboardingProgressResponse> completeDashboardTour(@AuthenticationPrincipal Jwt jwt,
                                                                            HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeDashboardTour(userId, request.getRemoteAddr())));
    }

    /**
     * Step 8 done (Story 3.8), the last one: "Criar meta e concluir" needs at least 1 active goal (409 otherwise);
     * "Pular por agora" ({@code skipped: true}) creates nothing. Marks the onboarding completed. 409 while on steps
     * 1 to 7; idempotent once completed (200, no change). Missing body means "not skipped".
     */
    @PostMapping("/me/finish")
    public ResponseEntity<OnboardingProgressResponse> finish(@AuthenticationPrincipal Jwt jwt,
                                                             @RequestBody(required = false) StepRequest body,
                                                             HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.finish(userId, skipped, request.getRemoteAddr())));
    }

    public record StepRequest(Boolean skipped) { }
}
