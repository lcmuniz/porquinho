package com.porquinho.service;

import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.ReconciliationResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Reconciliation;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.ReconciliationRepository;
import com.porquinho.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconciliationRepository reconciliationRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    @Mock
    private AuditLogService auditLogService;

    private ReconciliationService service;
    private UUID userId;
    private Account nubank;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(reconciliationRepository, accountRepository, transactionRepository, new BalanceHistoryRecorder(balanceHistoryRepository, java.time.Clock.systemUTC()),
            auditLogService);
        userId = UUID.randomUUID();
        nubank = new Account(userId, "Nubank", null, Account.AccountType.CHECKING, new BigDecimal("1000.00"));
        nubank.setId(UUID.randomUUID());
    }

    private void stubAccountAndSaves() {
        when(accountRepository.findActiveByIdForUpdate(nubank.getId(), userId)).thenReturn(Optional.of(nubank));
        when(accountRepository.save(any(Account.class))).thenAnswer(returnsFirstArg());
        when(reconciliationRepository.saveAndFlush(any(Reconciliation.class))).thenAnswer(returnsFirstArg());
    }

    private ReconciliationResponse.Created reconcile(String bankBalance, String reason) {
        return service.reconcile(userId, nubank.getId(),
            new CreateReconciliationRequest(new BigDecimal(bankBalance), reason), "127.0.0.1");
    }

    @Test
    void bankShowsMoreAdjustsTheBalanceAndRecordsEverything() {
        stubAccountAndSaves();

        ReconciliationResponse.Created result = reconcile("1050", "  Tarifa estornada ");

        assertEquals(new BigDecimal("1050.00"), nubank.getCurrentBalance());
        assertNotNull(nubank.getLastReconciliationDate());
        verify(accountRepository).save(nubank);

        ArgumentCaptor<Reconciliation> event = ArgumentCaptor.forClass(Reconciliation.class);
        verify(reconciliationRepository).saveAndFlush(event.capture());
        Reconciliation saved = event.getValue();
        assertEquals(nubank.getId(), saved.getAccountId());
        assertEquals(userId, saved.getUserId());
        assertEquals(new BigDecimal("1000.00"), saved.getPreviousBalance());
        assertEquals(new BigDecimal("1050.00"), saved.getBankBalance());
        assertEquals(new BigDecimal("50.00"), saved.getDifference());
        assertEquals("Tarifa estornada", saved.getReason());
        assertEquals(nubank.getLastReconciliationDate(), saved.getReconciledAt());

        ArgumentCaptor<AccountBalanceHistory> history = ArgumentCaptor.forClass(AccountBalanceHistory.class);
        verify(balanceHistoryRepository).save(history.capture());
        assertEquals(AccountBalanceHistory.EVENT_RECONCILIATION, history.getValue().getCreatedByEvent());
        assertEquals(new BigDecimal("1050.00"), history.getValue().getBalanceSnapshot());
        assertEquals(nubank.getId(), history.getValue().getAccountId());
        assertEquals(userId, history.getValue().getUserId());
        assertEquals(saved.getReconciledAt(), history.getValue().getSnapshotDate());

        verify(auditLogService).log(eq(ReconciliationService.AUDIT_RECONCILED), eq(userId), eq("127.0.0.1"),
            eq("{\"id\":\"" + nubank.getId() + "\",\"difference\":50.00}"));

        assertEquals(new BigDecimal("1050.00"), result.account().currentBalance());
        assertEquals(nubank.getLastReconciliationDate(), result.account().lastReconciliationDate());
        assertEquals(new BigDecimal("50.00"), result.reconciliation().difference());
    }

    @Test
    void bankShowsLessGivesNegativeDifference() {
        stubAccountAndSaves();

        ReconciliationResponse.Created result = reconcile("980.00", null);

        assertEquals(new BigDecimal("980.00"), nubank.getCurrentBalance());
        assertEquals(new BigDecimal("-20.00"), result.reconciliation().difference());
        assertNull(result.reconciliation().reason());
        verify(balanceHistoryRepository).save(any(AccountBalanceHistory.class));
        verify(auditLogService).log(anyString(), eq(userId), anyString(),
            eq("{\"id\":\"" + nubank.getId() + "\",\"difference\":-20.00}"));
    }

    @Test
    void equalBalancesRecordTheEventAndDateWithoutBalanceSnapshot() {
        stubAccountAndSaves();

        ReconciliationResponse.Created result = reconcile("1000", "   ");

        assertEquals(new BigDecimal("1000.00"), nubank.getCurrentBalance());
        assertNotNull(nubank.getLastReconciliationDate());
        assertEquals(0, result.reconciliation().difference().signum());
        assertNull(result.reconciliation().reason());
        verify(reconciliationRepository).saveAndFlush(any(Reconciliation.class));
        verifyNoInteractions(balanceHistoryRepository);
        verify(auditLogService).log(eq(ReconciliationService.AUDIT_RECONCILED), eq(userId), eq("127.0.0.1"),
            eq("{\"id\":\"" + nubank.getId() + "\",\"difference\":0.00}"));
    }

    @Test
    void negativeBankBalanceIsAllowed() {
        stubAccountAndSaves();

        ReconciliationResponse.Created result = reconcile("-30", null);

        assertEquals(new BigDecimal("-30.00"), nubank.getCurrentBalance());
        assertEquals(new BigDecimal("-1030.00"), result.reconciliation().difference());
    }

    @Test
    void differenceIsComputedFromTheServerBalance() {
        // Saldo mudou em outra aba: o servidor tem 900,00
        nubank.setCurrentBalance(new BigDecimal("900.00"));
        stubAccountAndSaves();

        ReconciliationResponse.Created result = reconcile("1050", null);

        assertEquals(new BigDecimal("900.00"), result.reconciliation().previousBalance());
        assertEquals(new BigDecimal("150.00"), result.reconciliation().difference());
    }

    @Test
    void unknownOtherUsersOrRemovedAccountIsNotFoundAndNothingChanges() {
        when(accountRepository.findActiveByIdForUpdate(nubank.getId(), userId)).thenReturn(Optional.empty());

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class, () -> reconcile("1050", null));

        assertEquals(AccountService.NOT_FOUND_MESSAGE, e.getMessage());
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(reconciliationRepository, balanceHistoryRepository, auditLogService);
    }

    @Test
    void invalidBankBalanceIsBadRequestEvenWithoutBeanValidation() {
        assertThrows(BadRequestException.class, () -> service.reconcile(userId, nubank.getId(),
            new CreateReconciliationRequest(null, null), "127.0.0.1"));
        assertThrows(BadRequestException.class, () -> reconcile("10.123", null));
        assertThrows(BadRequestException.class, () -> reconcile("10000000000000.00", null));
        verifyNoInteractions(accountRepository, reconciliationRepository, balanceHistoryRepository, auditLogService);
    }

    @Test
    void nullRequestOrNullBankBalanceIsBadRequestNotNullPointer() {
        BadRequestException noBody = assertThrows(BadRequestException.class,
            () -> service.reconcile(userId, nubank.getId(), null, "127.0.0.1"));
        BadRequestException noBalance = assertThrows(BadRequestException.class,
            () -> service.reconcile(userId, nubank.getId(), new CreateReconciliationRequest(null, "x"), "127.0.0.1"));

        assertEquals(ReconciliationService.REQUIRED_BALANCE_MESSAGE, noBody.getMessage());
        assertEquals("bankBalance", noBody.getField());
        assertEquals(ReconciliationService.REQUIRED_BALANCE_MESSAGE, noBalance.getMessage());
        assertEquals("bankBalance", noBalance.getField());
        verifyNoInteractions(accountRepository, reconciliationRepository, balanceHistoryRepository, auditLogService);
    }

    @Test
    void differenceOutsideDecimalRangeIsBadRequest() {
        nubank.setCurrentBalance(new BigDecimal("-9999999999999.99"));
        when(accountRepository.findActiveByIdForUpdate(nubank.getId(), userId)).thenReturn(Optional.of(nubank));

        BadRequestException e = assertThrows(BadRequestException.class, () -> reconcile("9999999999999.99", null));

        assertEquals(ReconciliationService.DIFFERENCE_OUT_OF_RANGE_MESSAGE, e.getMessage());
        assertEquals("bankBalance", e.getField());
        assertEquals(new BigDecimal("-9999999999999.99"), nubank.getCurrentBalance());
        verifyNoInteractions(reconciliationRepository, balanceHistoryRepository, auditLogService);
    }

    @Test
    void listReturnsTheRecentReconciliationsOfTheAccount() {
        Reconciliation r = new Reconciliation(nubank.getId(), userId, new BigDecimal("1000.00"),
            new BigDecimal("1050.00"), new BigDecimal("50.00"), null, OffsetDateTime.parse("2026-11-03T12:00:00Z"));
        r.setId(UUID.randomUUID());
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(nubank.getId(), userId))
            .thenReturn(Optional.of(nubank));
        when(reconciliationRepository.findRecentByAccountId(eq(nubank.getId()), eq(userId), any(Pageable.class)))
            .thenReturn(List.of(r));

        List<ReconciliationResponse> list = service.listRecent(userId, nubank.getId());

        assertEquals(1, list.size());
        assertEquals(r.getId(), list.get(0).id());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(reconciliationRepository).findRecentByAccountId(eq(nubank.getId()), eq(userId), page.capture());
        assertEquals(ReconciliationService.RECENT_LIMIT, page.getValue().getPageSize());
    }

    @Test
    void listOfAnotherUsersAccountIsNotFound() {
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(nubank.getId(), userId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listRecent(userId, nubank.getId()));
        verifyNoInteractions(reconciliationRepository);
    }
}
