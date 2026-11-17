package com.porquinho.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

import static com.porquinho.dto.CreateCreditCardRequest.DUE_DAY_MESSAGE;

/**
 * PUT /api/v1/credit-cards/{id} (Story 4.3). Same rules and cleaning as {@link CreateCreditCardRequest}, plus the
 * version read when the form opened (triagem 16/11, grupo B); a different current version is a 409
 * (CreditCardService.update).
 */
public record UpdateCreditCardRequest(
    @NotBlank(message = "Informe o nome do cartão")
    @Size(max = 100, message = "Use no máximo 100 caracteres no nome do cartão")
    String cardName,

    @Size(max = 100, message = "Use no máximo 100 caracteres no nome do banco")
    String bankName,

    @NotNull(message = "Informe o limite do cartão (use 0,00 se não souber)")
    @DecimalMin(value = "0", message = "O limite não pode ser negativo")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o limite com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal cardLimit,

    @NotNull(message = DUE_DAY_MESSAGE)
    @DecimalMin(value = "1", message = DUE_DAY_MESSAGE)
    @DecimalMax(value = "31", message = DUE_DAY_MESSAGE)
    @Digits(integer = 2, fraction = 0, message = DUE_DAY_MESSAGE)
    BigDecimal dueDay,

    @NotNull(message = "Versão obrigatória")
    @PositiveOrZero(message = "Versão inválida")
    Long version
) {
    public UpdateCreditCardRequest {
        // Same cleaning as the creation (CreateCreditCardRequest)
        CreateCreditCardRequest cleaned = new CreateCreditCardRequest(cardName, bankName, cardLimit, dueDay);
        cardName = cleaned.cardName();
        bankName = cleaned.bankName();
    }

    /** The validated due day as an int (call only after validation: 1..31, no fraction). */
    public int dueDayValue() {
        return dueDay.intValueExact();
    }
}
