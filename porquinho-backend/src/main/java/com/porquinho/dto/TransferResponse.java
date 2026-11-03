package com.porquinho.dto;

import com.porquinho.entity.Account;
import com.porquinho.entity.Transfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transfer returned by /api/v1/transfers (Story 4.5), with the names of both accounts (removed accounts included,
 * so the history stays readable). Money is a JSON number; the date is "yyyy-MM-dd".
 */
public record TransferResponse(
    UUID id,
    UUID fromAccountId,
    String fromAccountName,
    UUID toAccountId,
    String toAccountName,
    BigDecimal amount,
    LocalDate transferDate,
    String description,
    OffsetDateTime createdAt
) {
    /** {@code accounts} maps account id to account; a missing id gives a null name. */
    public static TransferResponse of(Transfer transfer, Map<UUID, Account> accounts) {
        return new TransferResponse(
            transfer.getId(),
            transfer.getFromAccountId(),
            name(accounts.get(transfer.getFromAccountId())),
            transfer.getToAccountId(),
            name(accounts.get(transfer.getToAccountId())),
            transfer.getAmount(),
            transfer.getTransferDate(),
            transfer.getDescription(),
            transfer.getCreatedAt()
        );
    }

    private static String name(Account account) {
        return account == null ? null : account.getAccountName();
    }
}
