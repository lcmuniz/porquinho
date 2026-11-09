package com.porquinho.service;

import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The only writer of account_balance_history (retro Epic 4, X5). Every service that changes an account's current
 * balance records the snapshot here, inside its own transaction, so the rules live in one place:
 * <ul>
 *   <li>the snapshot holds the account's current balance after the change;</li>
 *   <li>snapshot_date always comes from the injected {@link Clock} (or from a moment the caller took from
 *   {@link #now()}, when the same instant must also be stored elsewhere, as in a reconciliation);</li>
 *   <li>{@link #MAX_BALANCE} is the limit of DECIMAL(15,2) shared by every balance check.</li>
 * </ul>
 * The transactions of Epic 5 must go through here as well.
 */
@Component
public class BalanceHistoryRecorder {

    /** Largest absolute value that fits DECIMAL(15,2). */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");

    /** What sets the balance by hand: the transactions up to it are already in the balance (decision D3). */
    private static final List<String> REFERENCE_EVENTS = List.of(AccountBalanceHistory.EVENT_INITIAL,
        AccountBalanceHistory.EVENT_EDIT, AccountBalanceHistory.EVENT_RECONCILIATION);

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final AccountBalanceHistoryRepository balanceHistoryRepository;
    private final Clock clock;

    public BalanceHistoryRecorder(AccountBalanceHistoryRepository balanceHistoryRepository, Clock clock) {
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.clock = clock;
    }

    /** True when the absolute value fits DECIMAL(15,2). */
    public static boolean fits(BigDecimal value) {
        return value.abs().compareTo(MAX_BALANCE) <= 0;
    }

    /** The current moment, from the same clock used for snapshot_date. */
    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /**
     * Decision D3: the São Paulo date of the last time the balance was set by hand: the last initial, edit or
     * reconciliation snapshot, or the last reconciliation, which leaves no snapshot when it finds no difference. Only
     * transactions dated after it change the balance.
     */
    public LocalDate referenceDate(Account account) {
        OffsetDateTime reference = balanceHistoryRepository
            .findLastSnapshotDate(account.getId(), account.getUserId(), REFERENCE_EVENTS)
            .orElse(account.getCreatedAt());
        OffsetDateTime reconciled = account.getLastReconciliationDate();
        if (reconciled != null && reconciled.isAfter(reference)) {
            reference = reconciled;
        }
        return reference.atZoneSameInstant(SAO_PAULO).toLocalDate();
    }

    /** Records the account's current balance, dated now. */
    public AccountBalanceHistory record(Account account, String event) {
        return record(account, event, now(), null);
    }

    /** Records the account's current balance at a moment taken from {@link #now()}. */
    public AccountBalanceHistory record(Account account, String event, OffsetDateTime at) {
        return record(account, event, at, null);
    }

    /** Records one side of a transfer, dated now and linked to the transfer. */
    public AccountBalanceHistory recordTransfer(Account account, UUID transferId) {
        return record(account, AccountBalanceHistory.EVENT_TRANSFER, now(), transferId);
    }

    private AccountBalanceHistory record(Account account, String event, OffsetDateTime at, UUID transferId) {
        AccountBalanceHistory snapshot = new AccountBalanceHistory(account.getId(), account.getUserId(),
            account.getCurrentBalance(), event);
        snapshot.setSnapshotDate(at);
        snapshot.setTransferId(transferId);
        return balanceHistoryRepository.save(snapshot);
    }
}
