package com.porquinho.dto;

import java.util.UUID;

/** POST /api/v1/onboarding/me/import (Story 5.10): the finished import. Without it the step counts as skipped. */
public record CompleteOnboardingImportRequest(UUID importId) {
}
