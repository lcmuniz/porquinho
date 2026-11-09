package com.porquinho.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PUT /api/v1/transactions/{id} (Story 5.12). Exactly one of {@code accountId} / {@code creditCardId} (checked by the
 * service); {@code categoryId} null leaves the transaction without category. A future date is allowed.
 */
public record TransactionUpdateRequest(
    @NotNull(message = "Informe a data")
    LocalDate date,

    @NotBlank(message = "Informe a descrição")
    @Size(max = 500, message = "Use no máximo 500 caracteres na descrição")
    String description,

    @NotNull(message = "Informe o valor")
    @DecimalMin(value = "0.01", message = "Informe um valor maior que zero")
    @DecimalMax(value = "9999999999999.99", message = "Informe um valor de no máximo 13 dígitos antes da vírgula")
    @Digits(integer = 13, fraction = 2, message = "Informe o valor com até 2 casas decimais")
    BigDecimal amount,

    @NotNull(message = "Escolha o tipo: débito ou crédito")
    @Pattern(regexp = "DEBIT|CREDIT", message = "Escolha o tipo: débito ou crédito")
    String type,

    UUID categoryId,
    UUID accountId,
    UUID creditCardId
) {
    public TransactionUpdateRequest {
        description = description == null ? null : description.strip().replaceAll("\\s+", " ");
    }
}
