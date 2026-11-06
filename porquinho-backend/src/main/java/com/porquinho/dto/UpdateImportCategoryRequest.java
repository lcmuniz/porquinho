package com.porquinho.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * PATCH /api/v1/imports/{id}/transactions/category (Story 5.10): one id for the single edit, several for the bulk one.
 */
public record UpdateImportCategoryRequest(
    @NotEmpty(message = "Escolha ao menos uma transação")
    @Size(max = 5000, message = "Altere no máximo 5000 transações de uma vez")
    List<@NotNull(message = "Escolha ao menos uma transação") UUID> transactionIds,
    @NotNull(message = "Escolha uma categoria")
    UUID categoryId
) {
}
