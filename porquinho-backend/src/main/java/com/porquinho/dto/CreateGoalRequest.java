package com.porquinho.dto;

import com.porquinho.service.CategoryService;
import com.porquinho.validation.NotPastInSaoPaulo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /api/v1/goals (Story 3.8). The name is cleaned before validation (compact constructor runs on
 * deserialization) like category names: NFC, no Unicode spaces (NBSP) nor format characters (U+200B, U+FEFF)
 * at the edges, so an invisible-only name is blank. The target amount must be > 0 and fit DECIMAL(15,2);
 * the target date is optional and may not be before today in America/Sao_Paulo; the monthly allocation is
 * optional, >= 0 and fits DECIMAL(15,2). Story 8.1: the optional description (up to 500 characters) is cleaned
 * the same way and a blank one becomes null.
 */
public record CreateGoalRequest(
    @NotBlank(message = "Informe o nome da meta")
    @Size(max = 100, message = "Use no máximo 100 caracteres no nome da meta")
    String name,

    @NotNull(message = "Informe o valor-alvo da meta")
    @DecimalMin(value = "0", inclusive = false, message = "O valor-alvo precisa ser maior que zero")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o valor-alvo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal targetAmount,

    @NotPastInSaoPaulo(message = "Escolha uma data de hoje em diante")
    LocalDate targetDate,

    @DecimalMin(value = "0", message = "O aporte mensal não pode ser negativo")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o aporte mensal com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal monthlyAllocation,

    @Size(max = 500, message = "Use no máximo 500 caracteres na descrição")
    String description
) {
    public CreateGoalRequest {
        name = CategoryService.cleanName(name);
        description = CategoryService.cleanName(description);
        if (description != null && description.isEmpty()) description = null;
    }
}
