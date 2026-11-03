package com.porquinho.dto;

import com.porquinho.service.CategoryService;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * PUT /api/v1/accounts/{id} (Story 4.3). Same rules and cleaning as {@link CreateAccountRequest}, with the
 * current balance in place of the initial one (the initial balance never changes).
 */
public record UpdateAccountRequest(
    @NotBlank(message = "Informe o nome da conta")
    @Size(max = 100, message = "Use no máximo 100 caracteres no nome da conta")
    String accountName,

    @Size(max = 100, message = "Use no máximo 100 caracteres no nome do banco")
    String bankName,

    @NotBlank(message = "Escolha o tipo da conta: corrente ou poupança")
    @Pattern(regexp = "CHECKING|SAVINGS", message = "Escolha o tipo da conta: corrente ou poupança")
    String accountType,

    @NotNull(message = "Informe o saldo atual (use 0,00 se não souber)")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal currentBalance
) {
    public UpdateAccountRequest {
        accountName = CategoryService.cleanName(accountName);
        bankName = CategoryService.cleanName(bankName);
        if (bankName != null && bankName.isEmpty()) {
            bankName = null;
        }
    }
}
