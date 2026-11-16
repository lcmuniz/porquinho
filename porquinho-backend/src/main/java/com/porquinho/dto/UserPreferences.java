package com.porquinho.dto;

import jakarta.validation.constraints.NotNull;

/** GET/PUT /api/v1/users/me/preferences (pre-Epic 10): the user's settings. */
public record UserPreferences(
    @NotNull(message = "Informe se quer receber o alerta de orçamento por e-mail")
    Boolean budgetAlertEmail
) {
}
