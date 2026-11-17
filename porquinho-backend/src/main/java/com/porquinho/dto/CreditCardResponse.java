package com.porquinho.dto;

import com.porquinho.entity.CreditCard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Credit card as returned by /api/v1/credit-cards (Story 3.3). Money is a JSON number; version goes back
 * in the edit (PUT) for the optimistic check.
 */
public record CreditCardResponse(
    UUID id,
    String cardName,
    String bankName,
    BigDecimal cardLimit,
    int dueDay,
    OffsetDateTime createdAt,
    Long version
) {
    public static CreditCardResponse of(CreditCard card) {
        return new CreditCardResponse(
            card.getId(),
            card.getCardName(),
            card.getBankName(),
            card.getCardLimit(),
            card.getDueDay(),
            card.getCreatedAt(),
            card.getVersion()
        );
    }
}
