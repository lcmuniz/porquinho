package com.porquinho.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** POST /api/v1/budgets/{YYYY-MM}/reallocations (Story 6.5): move {@code amount} from one child category to another. */
public record ReallocateBudgetRequest(
    @NotNull(message = "Escolha a categoria de origem")
    UUID fromCategoryId,

    @NotNull(message = "Escolha a categoria de destino")
    UUID toCategoryId,

    @NotNull(message = "Informe o valor")
    @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero")
    @Digits(integer = 13, fraction = 2, message = "Informe o valor com até 2 casas decimais")
    BigDecimal amount
) {
}
