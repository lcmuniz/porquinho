package com.porquinho.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of an import in the review (Story 5.9). {@code category} comes resolved, removed categories included, and is
 * null when the row has none. {@code pixDetails} is the object written by the Pix detection (Story 5.6), with its keys
 * as stored.
 */
public record ImportTransactionResponse(
    UUID id,
    LocalDate date,
    String description,
    BigDecimal amount,
    String type,
    String kind,
    Category category,
    String source,
    Integer confidence,
    String reviewStatus,
    @JsonProperty("isPix") boolean isPix,
    JsonNode pixDetails,
    @JsonProperty("isInstallment") boolean isInstallment,
    Integer installmentNumber,
    Integer installmentTotal,
    @JsonProperty("isFutureInstallment") boolean isFutureInstallment
) {
    /** {@code group} is the name of the parent category; null for a category without one. */
    public record Category(UUID id, String name, String group) {
    }
}
