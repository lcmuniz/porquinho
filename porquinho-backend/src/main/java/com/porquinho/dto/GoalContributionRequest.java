package com.porquinho.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** POST /api/v1/goals/{id}/contributions (Story 8.2): required, > 0, fits DECIMAL(15,2). No withdrawals. */
public record GoalContributionRequest(
    @NotNull(message = "Informe o valor do aporte")
    @DecimalMin(value = "0", inclusive = false, message = "O aporte precisa ser maior que zero")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o aporte com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal amount
) {
}
