package com.porquinho.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * PUT /api/v1/goals/allocations (Story 8.5): several goals' monthly allocations at once. Each item follows the
 * rules of {@link UpdateGoalAllocationRequest}; a repeated goal id is rejected by the service.
 */
public record UpdateGoalAllocationsRequest(
    @NotEmpty(message = "Informe ao menos uma meta")
    @Valid
    List<@NotNull(message = "Informe a meta") Item> items
) {

    public record Item(
        @NotNull(message = "Informe a meta")
        UUID goalId,

        @NotNull(message = "Informe o aporte mensal")
        @DecimalMin(value = "0", message = "O aporte mensal não pode ser negativo")
        @Digits(integer = 13, fraction = 2,
            message = "Informe o aporte mensal com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
        BigDecimal monthlyAllocation
    ) {
    }
}
