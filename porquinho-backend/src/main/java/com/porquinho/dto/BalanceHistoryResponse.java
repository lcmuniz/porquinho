package com.porquinho.dto;

import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Balance history returned by /api/v1/accounts/{id}/balance-history (Story 4.7): the account, the start of the period
 * (null for "all") and its snapshots, oldest first. Money is a JSON number.
 */
public record BalanceHistoryResponse(AccountSummary account, Instant periodStart, List<Point> points) {

    public record AccountSummary(UUID id, String accountName, BigDecimal currentBalance) {
        public static AccountSummary of(Account account) {
            return new AccountSummary(account.getId(), account.getAccountName(), account.getCurrentBalance());
        }
    }

    public record Point(OffsetDateTime date, BigDecimal balance, String event) {
        public static Point of(AccountBalanceHistory snapshot) {
            return new Point(snapshot.getSnapshotDate(), snapshot.getBalanceSnapshot(), snapshot.getCreatedByEvent());
        }
    }

    public static BalanceHistoryResponse of(Account account, OffsetDateTime periodStart,
                                            List<AccountBalanceHistory> snapshots) {
        return new BalanceHistoryResponse(AccountSummary.of(account),
            periodStart == null ? null : periodStart.toInstant(),
            snapshots.stream().map(Point::of).toList());
    }
}
