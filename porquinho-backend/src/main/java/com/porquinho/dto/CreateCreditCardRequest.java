package com.porquinho.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/v1/credit-cards (Story 3.3). Names are stripped before validation (compact constructor runs on
 * deserialization; Unicode spaces such as NBSP included), so "  Nubank  " is stored as "Nubank" and a blank bank becomes null.
 * The limit must be >= 0 and fit DECIMAL(15,2). The due day is read as a decimal on purpose: with an Integer,
 * Jackson would silently truncate 10.5 to 10; here a fractional day fails validation with the field message.
 */
public record CreateCreditCardRequest(
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
    BigDecimal dueDay
) {
    public static final String DUE_DAY_MESSAGE = "Informe um dia entre 1 e 31";

    /** Leading/trailing whitespace, Unicode spaces included (NBSP is not stripped by trim()/strip()). */
    private static final java.util.regex.Pattern EDGE_SPACES =
        java.util.regex.Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");

    public CreateCreditCardRequest {
        cardName = clean(cardName);
        bankName = clean(bankName);
        if (bankName != null && bankName.isEmpty()) {
            bankName = null;
        }
    }

    /** The validated due day as an int (call only after validation: 1..31, no fraction). */
    public int dueDayValue() {
        return dueDay.intValueExact();
    }

    private static String clean(String value) {
        return value == null ? null : EDGE_SPACES.matcher(value).replaceAll("");
    }
}
