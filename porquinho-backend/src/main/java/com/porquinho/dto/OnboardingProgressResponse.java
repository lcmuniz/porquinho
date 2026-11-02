package com.porquinho.dto;

import com.porquinho.entity.OnboardingProgress;

import java.util.List;

/**
 * GET /api/v1/onboarding/me and POST /api/v1/onboarding/me/tutorial (Story 3.1) /me/accounts (Story 3.2) and /me/cards (Story 3.3).
 */
public record OnboardingProgressResponse(
    int currentStep,
    List<String> stepsCompleted,
    boolean completed
) {
    public static OnboardingProgressResponse of(OnboardingProgress progress) {
        return new OnboardingProgressResponse(
            progress.getCurrentStep(),
            progress.getSteps(),
            progress.isCompleted()
        );
    }
}
