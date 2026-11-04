package com.porquinho.dto;

import com.porquinho.entity.Account;
import com.porquinho.entity.Reconciliation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reconciliation returned by /api/v1/accounts/{id}/reconciliations (Story 4.6). Money is a JSON number.
 */
public record ReconciliationResponse(
    UUID id,
    UUID accountId,
    BigDecimal previousBalance,
    BigDecimal bankBalance,
    BigDecimal difference,
    String reason,
    OffsetDateTime reconciledAt
) {
    public static ReconciliationResponse of(Reconciliation reconciliation) {
        return new ReconciliationResponse(
            reconciliation.getId(),
            reconciliation.getAccountId(),
            reconciliation.getPreviousBalance(),
            reconciliation.getBankBalance(),
            reconciliation.getDifference(),
            reconciliation.getReason(),
            reconciliation.getReconciledAt()
        );
    }

    /** Body of the 201: the account with its new balance and date, and the recorded event. */
    public record Created(AccountResponse account, ReconciliationResponse reconciliation) {
        public static Created of(Account account, Reconciliation reconciliation) {
            return new Created(AccountResponse.of(account), ReconciliationResponse.of(reconciliation));
        }
    }
}
