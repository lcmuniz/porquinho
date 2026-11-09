package com.porquinho.service;

import com.porquinho.entity.Account;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCoreTest {

    @Mock
    private TransactionImportRepository transactionImportRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BalanceHistoryRecorder balanceHistoryRecorder;

    private TransactionCore core() {
        return new TransactionCore(transactionImportRepository, categoryRepository, balanceHistoryRecorder);
    }

    @Test
    void lockTargetsTakesDistinctKeysInAscendingOrderIgnoringNulls() {
        UUID high = new UUID(0, 9);
        UUID low = new UUID(0, 2);

        core().lockTargets(high, null, low, high);

        InOrder order = inOrder(transactionImportRepository);
        order.verify(transactionImportRepository).lockTarget(TransactionCore.lockKey(low));
        order.verify(transactionImportRepository).lockTarget(TransactionCore.lockKey(high));
        verify(transactionImportRepository, times(2)).lockTarget(anyLong());
    }

    @Test
    void lockTargetsWithOnlyNullsLocksNothing() {
        core().lockTargets(null, null);

        verifyNoInteractions(transactionImportRepository);
    }

    @Test
    void inBalanceFollowsDecisionD3() {
        Account account = new Account();
        LocalDate reference = LocalDate.of(2026, 3, 10);
        when(balanceHistoryRecorder.referenceDate(account)).thenReturn(reference);
        TransactionCore core = core();

        assertThat(core.inBalance(null, reference.plusDays(1))).isFalse();
        assertThat(core.inBalance(account, reference)).isFalse();
        assertThat(core.inBalance(account, reference.plusDays(1))).isTrue();
    }
}
