package com.porquinho.service;

import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.when;

/** The single writer of account_balance_history (retro Epic 4, X5). */
@ExtendWith(MockitoExtension.class)
class BalanceHistoryRecorderTest {

    private static final Instant FIXED = Instant.parse("2026-11-04T15:00:00Z");

    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    private BalanceHistoryRecorder recorder;
    private Account account;

    @BeforeEach
    void setUp() {
        recorder = new BalanceHistoryRecorder(balanceHistoryRepository, Clock.fixed(FIXED, ZoneOffset.UTC));
        account = new Account(UUID.randomUUID(), "Nubank", null, Account.AccountType.CHECKING, new BigDecimal("10.00"));
        account.setId(UUID.randomUUID());
        account.setCurrentBalance(new BigDecimal("42.50"));
    }

    @Test
    void recordsTheCurrentBalanceDatedByTheClock() {
        when(balanceHistoryRepository.save(any(AccountBalanceHistory.class))).thenAnswer(returnsFirstArg());

        AccountBalanceHistory snapshot = recorder.record(account, AccountBalanceHistory.EVENT_EDIT);

        assertThat(snapshot.getAccountId()).isEqualTo(account.getId());
        assertThat(snapshot.getUserId()).isEqualTo(account.getUserId());
        assertThat(snapshot.getBalanceSnapshot()).isEqualByComparingTo("42.50");
        assertThat(snapshot.getCreatedByEvent()).isEqualTo(AccountBalanceHistory.EVENT_EDIT);
        assertThat(snapshot.getSnapshotDate().toInstant()).isEqualTo(FIXED);
        assertThat(snapshot.getTransferId()).isNull();
    }

    @Test
    void recordsAtTheMomentGivenByTheCaller() {
        when(balanceHistoryRepository.save(any(AccountBalanceHistory.class))).thenAnswer(returnsFirstArg());
        OffsetDateTime at = recorder.now();

        AccountBalanceHistory snapshot = recorder.record(account, AccountBalanceHistory.EVENT_RECONCILIATION, at);

        assertThat(snapshot.getSnapshotDate()).isEqualTo(at);
        assertThat(at.toInstant()).isEqualTo(FIXED);
    }

    @Test
    void transferSnapshotIsLinkedToTheTransfer() {
        when(balanceHistoryRepository.save(any(AccountBalanceHistory.class))).thenAnswer(returnsFirstArg());
        UUID transferId = UUID.randomUUID();

        AccountBalanceHistory snapshot = recorder.recordTransfer(account, transferId);

        assertThat(snapshot.getCreatedByEvent()).isEqualTo(AccountBalanceHistory.EVENT_TRANSFER);
        assertThat(snapshot.getTransferId()).isEqualTo(transferId);
        assertThat(snapshot.getSnapshotDate().toInstant()).isEqualTo(FIXED);
    }

    @Test
    void fitsMatchesDecimal15Scale2() {
        assertThat(BalanceHistoryRecorder.fits(new BigDecimal("9999999999999.99"))).isTrue();
        assertThat(BalanceHistoryRecorder.fits(new BigDecimal("-9999999999999.99"))).isTrue();
        assertThat(BalanceHistoryRecorder.fits(new BigDecimal("10000000000000.00"))).isFalse();
        assertThat(BalanceHistoryRecorder.fits(new BigDecimal("-10000000000000.00"))).isFalse();
    }
}
