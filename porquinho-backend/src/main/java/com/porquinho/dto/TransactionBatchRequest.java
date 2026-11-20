package com.porquinho.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * POST /api/v1/transactions/batch/category and /batch/delete (edição em lote em /transacoes): the selected
 * transactions. {@code categoryId} is the new category (null = no category); the delete ignores it.
 */
public record TransactionBatchRequest(
    @NotEmpty(message = "Selecione ao menos uma transação")
    @Size(max = 200, message = "Selecione no máximo 200 transações")
    List<@NotNull UUID> ids,

    UUID categoryId
) {
}
