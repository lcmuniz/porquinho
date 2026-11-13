package com.porquinho.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** PUT /api/v1/goals/{id}/allocation (Story 8.2): required, >= 0, fits DECIMAL(15,2). */
public record UpdateGoalAllocationRequest(
    @NotNull(message = "Informe o aporte mensal")
    @DecimalMin(value = "0", message = "O aporte mensal não pode ser negativo")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o aporte mensal com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal monthlyAllocation
) {
}
