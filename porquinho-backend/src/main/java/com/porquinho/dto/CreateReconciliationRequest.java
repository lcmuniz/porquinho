package com.porquinho.dto;

import com.porquinho.service.CategoryService;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/v1/accounts/{id}/reconciliations (Story 4.6). The bank balance is required, may be negative and must fit
 * DECIMAL(15,2) with at most 2 decimals; the reason is optional, cleaned once like names (NFC, no Unicode spaces nor
 * format characters at the edges), blank becomes null. The difference is always computed by the server.
 */
public record CreateReconciliationRequest(
    @NotNull(message = "Informe o saldo no banco")
    @Digits(integer = 13, fraction = 2,
        message = "Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula")
    BigDecimal bankBalance,

    @Size(max = 200, message = "Use no máximo 200 caracteres no motivo")
    String reason
) {
    public CreateReconciliationRequest {
        reason = CategoryService.cleanName(reason);
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
    }
}
