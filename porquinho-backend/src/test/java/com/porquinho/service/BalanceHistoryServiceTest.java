package com.porquinho.service;

import com.porquinho.dto.BalanceHistoryResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceHistoryServiceTest {

    /** 2026-11-04 12:00 in Sao Paulo (UTC-3). */
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T15:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    private BalanceHistoryService service;
    private UUID userId;
    private Account nubank;

    @BeforeEach
    void setUp() {
        service = new BalanceHistoryService(accountRepository, balanceHistoryRepository,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        userId = UUID.randomUUID();
        nubank = new Account(userId, "Nubank", null, Account.AccountType.CHECKING, new BigDecimal("1000.00"));
        nubank.setId(UUID.randomUUID());
    }

    private AccountBalanceHistory snapshot(String balance, String event, OffsetDateTime date) {
        AccountBalanceHistory h = new AccountBalanceHistory(nubank.getId(), userId, new BigDecimal(balance), event);
        h.setSnapshotDate(date);
        return h;
    }

    private void stubAccount() {
        when(accountRepository.findByIdAndUserId(nubank.getId(), userId)).thenReturn(Optional.of(nubank));
    }

    @Test
    void periodStartsBeforeThePeriodPointThenThePeriodPoints() {
        stubAccount();
        AccountBalanceHistory before = snapshot("1000.00", AccountBalanceHistory.EVENT_INITIAL, NOW.minusMonths(5));
        AccountBalanceHistory edit = snapshot("1200.00", AccountBalanceHistory.EVENT_EDIT, NOW.minusDays(10));
        when(balanceHistoryRepository.findByAccountBefore(eq(nubank.getId()), eq(userId), any(), any(Pageable.class)))
            .thenReturn(List.of(before));
        when(balanceHistoryRepository.findByAccountSince(eq(nubank.getId()), eq(userId), any()))
            .thenReturn(List.of(edit));

        BalanceHistoryResponse response = service.history(userId, nubank.getId(), "3m");

        assertEquals(nubank.getId(), response.account().id());
        assertEquals("Nubank", response.account().accountName());
        assertEquals(new BigDecimal("1000.00"), response.account().currentBalance());
        assertEquals(2, response.points().size());
        assertEquals("initial", response.points().get(0).event());
        assertEquals(new BigDecimal("1200.00"), response.points().get(1).balance());
        assertEquals("edit", response.points().get(1).event());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(balanceHistoryRepository).findByAccountBefore(eq(nubank.getId()), eq(userId), any(), page.capture());
        assertEquals(1, page.getValue().getPageSize());
    }

    @Test
    void periodIsCountedFromNowInSaoPaulo() {
        stubAccount();
        service.history(userId, nubank.getId(), "30d");
        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(balanceHistoryRepository).findByAccountSince(eq(nubank.getId()), eq(userId), from.capture());
        assertEquals(NOW.minusDays(30).toInstant(), from.getValue().toInstant());
        assertEquals(ZoneOffset.ofHours(-3), from.getValue().getOffset());
    }

    @Test
    void responseCarriesThePeriodStart() {
        stubAccount();
        assertEquals(NOW.minusMonths(3).toInstant(), service.history(userId, nubank.getId(), "3m").periodStart());
    }

    @Test
    void eachRangeMapsToItsStart() {
        stubAccount();
        service.history(userId, nubank.getId(), "6m");
        service.history(userId, nubank.getId(), "1y");
        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(balanceHistoryRepository, times(2)).findByAccountSince(eq(nubank.getId()), eq(userId), from.capture());
        assertEquals(NOW.minusMonths(6).toInstant(), from.getAllValues().get(0).toInstant());
        assertEquals(NOW.minusYears(1).toInstant(), from.getAllValues().get(1).toInstant());
    }

    @Test
    void nullRangeDefaultsToThreeMonths() {
        stubAccount();
        service.history(userId, nubank.getId(), null);
        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(balanceHistoryRepository).findByAccountSince(eq(nubank.getId()), eq(userId), from.capture());
        assertEquals(NOW.minusMonths(3).toInstant(), from.getValue().toInstant());
    }

    @Test
    void lastEventBeforeThePeriodIsTheOnlyPoint() {
        stubAccount();
        AccountBalanceHistory old = snapshot("1050.00", AccountBalanceHistory.EVENT_RECONCILIATION, NOW.minusMonths(2));
        when(balanceHistoryRepository.findByAccountBefore(eq(nubank.getId()), eq(userId), any(), any(Pageable.class)))
            .thenReturn(List.of(old));

        BalanceHistoryResponse response = service.history(userId, nubank.getId(), "30d");

        assertEquals(1, response.points().size());
        assertEquals(new BigDecimal("1050.00"), response.points().get(0).balance());
        assertEquals("reconciliation", response.points().get(0).event());
    }

    @Test
    void allReturnsEverySnapshotWithoutThePreviousPointQuery() {
        stubAccount();
        when(balanceHistoryRepository.findByAccount(nubank.getId(), userId)).thenReturn(List.of(
            snapshot("1000.00", AccountBalanceHistory.EVENT_INITIAL, NOW.minusYears(3)),
            snapshot("-50.00", AccountBalanceHistory.EVENT_EDIT, NOW.minusDays(1))));

        BalanceHistoryResponse response = service.history(userId, nubank.getId(), "all");

        assertEquals(2, response.points().size());
        assertEquals(new BigDecimal("-50.00"), response.points().get(1).balance());
        assertNull(response.periodStart());
        verify(balanceHistoryRepository, never()).findByAccountBefore(any(), any(), any(), any());
        verify(balanceHistoryRepository, never()).findByAccountSince(any(), any(), any());
    }

    @Test
    void invalidRangeIs400BeforeAnyQuery() {
        BadRequestException ex = assertThrows(BadRequestException.class,
            () -> service.history(userId, nubank.getId(), "abc"));
        assertEquals("Período inválido", ex.getMessage());
        verifyNoInteractions(accountRepository, balanceHistoryRepository);
    }

    @Test
    void anotherUsersOrMissingAccountIs404() {
        when(accountRepository.findByIdAndUserId(nubank.getId(), userId)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.history(userId, nubank.getId(), "3m"));
        assertEquals("Conta não encontrada", ex.getMessage());
        verifyNoInteractions(balanceHistoryRepository);
    }

    @Test
    void removedAccountIsStillReadable() {
        nubank.setDeletedAt(NOW.minusDays(1));
        stubAccount();
        BalanceHistoryResponse response = service.history(userId, nubank.getId(), "3m");
        assertEquals(nubank.getId(), response.account().id());
    }
}
