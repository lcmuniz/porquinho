package com.porquinho.dto;

import com.porquinho.entity.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bank account as returned by /api/v1/accounts (Story 3.2). Money is a JSON number. Story 4.6:
 * lastReconciliationDate is null until the first reconciliation. Retro do Epic 4: version is sent back on the edit
 * (409 when another screen changed the account in between).
 */
public record AccountResponse(
    UUID id,
    String accountName,
    String bankName,
    String accountType,
    BigDecimal initialBalance,
    BigDecimal currentBalance,
    OffsetDateTime createdAt,
    OffsetDateTime lastReconciliationDate,
    Long version
) {
    public static AccountResponse of(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getAccountName(),
            account.getBankName(),
            account.getAccountType().name(),
            account.getInitialBalance(),
            account.getCurrentBalance(),
            account.getCreatedAt(),
            account.getLastReconciliationDate(),
            account.getVersion()
        );
    }
}
