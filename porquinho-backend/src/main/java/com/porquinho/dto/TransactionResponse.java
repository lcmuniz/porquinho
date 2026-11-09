package com.porquinho.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One confirmed transaction of /transacoes (Story 5.12): the fields of {@link ImportTransactionResponse} plus where it
 * is and, for an installment, how many future and confirmed installments of its purchase are still active.
 */
public record TransactionResponse(
    UUID id,
    LocalDate date,
    String description,
    BigDecimal amount,
    String type,
    String kind,
    ImportTransactionResponse.Category category,
    String source,
    Integer confidence,
    String reviewStatus,
    @JsonProperty("isPix") boolean isPix,
    JsonNode pixDetails,
    @JsonProperty("isInstallment") boolean isInstallment,
    Integer installmentNumber,
    Integer installmentTotal,
    @JsonProperty("isFutureInstallment") boolean isFutureInstallment,
    UUID accountId,
    UUID creditCardId,
    UUID installmentGroupId,
    int remainingInstallments,
    /** Active confirmed installments of the purchase, this one included: at 1, removing it takes the futures (Q3). */
    int confirmedInstallments
) {
}
