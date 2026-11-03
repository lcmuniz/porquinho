package com.porquinho.dto;

import com.porquinho.service.CategoryService;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/v1/accounts (Story 3.2). Names are trimmed before validation (compact constructor runs on
 * deserialization; Unicode spaces such as NBSP included), so "  Nubank  " is stored as "Nubank" and a blank bank becomes null.
 * The balance may be negative (overdraft, decision 1A) and must fit DECIMAL(15,2).
 */
public record CreateAccountRequest(
    @NotBlank(message = "Informe o nome da conta")
    @Size(max = 100, message = "Use no máximo 100 caracteres no nome da conta")
    String accountName,

    @Size(max = 100, message = "Use no máximo 100 caracteres no nome do banco")
    String bankName,

    @NotBlank(message = "Escolha o tipo da conta: corrente ou poupança")
    @Pattern(regexp = "CHECKING|SAVINGS", message = "Escolha o tipo da conta: corrente ou poupança")
    String accountType,

    @NotNull(message = "Informe o saldo inicial (use 0,00 se não souber)")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal initialBalance
) {
    public CreateAccountRequest {
        accountName = clean(accountName);
        bankName = clean(bankName);
        if (bankName != null && bankName.isEmpty()) {
            bankName = null;
        }
    }

    /** Same cleaning as category and goal names: NFC, no edge whitespace or invisible characters. */
    private static String clean(String value) {
        return CategoryService.cleanName(value);
    }
}
