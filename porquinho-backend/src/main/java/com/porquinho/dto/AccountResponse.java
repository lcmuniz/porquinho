package com.porquinho.dto;

import com.porquinho.entity.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bank account as returned by /api/v1/accounts (Story 3.2). Money is a JSON number.
 */
public record AccountResponse(
    UUID id,
    String accountName,
    String bankName,
    String accountType,
    BigDecimal initialBalance,
    BigDecimal currentBalance,
    OffsetDateTime createdAt
) {
    public static AccountResponse of(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getAccountName(),
            account.getBankName(),
            account.getAccountType().name(),
            account.getInitialBalance(),
            account.getCurrentBalance(),
            account.getCreatedAt()
        );
    }
}
