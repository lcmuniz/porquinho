package com.porquinho.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * PUT /api/v1/budgets/{YYYY-MM} (Story 6.3): the whole set of allocations of the month. A category left out has no
 * allocation. Repeated, group, removed or foreign categories are rejected by the service.
 */
public record SaveBudgetRequest(
    @NotNull(message = "Informe as alocações")
    List<@Valid @NotNull(message = "Informe a alocação") Item> allocations
) {
    public record Item(
        @NotNull(message = "Escolha uma categoria")
        UUID categoryId,

        @NotNull(message = "Informe o valor")
        @DecimalMin(value = "0", message = "O valor não pode ser negativo")
        @Digits(integer = 13, fraction = 2, message = "Informe o valor com até 2 casas decimais")
        BigDecimal amount
    ) {
    }
}
