package com.porquinho.controller;

import com.porquinho.dto.OnboardingProgressResponse;
import com.porquinho.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
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
 * Guided onboarding endpoints (Story 3.1). The user always comes from the JWT.
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
                                                                       @RequestBody(required = false) TutorialRequest body,
                                                                       HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
        return ResponseEntity.ok(OnboardingProgressResponse.of(
            onboardingService.completeTutorial(userId, skipped, request.getRemoteAddr())));
    }

    public record TutorialRequest(Boolean skipped) { }
}
