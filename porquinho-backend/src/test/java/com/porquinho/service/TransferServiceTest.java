package com.porquinho.service;

import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.TransferResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Transfer;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private TransferService service;
    private UUID userId;
    private Account nubank;
    private Account inter;

    @BeforeEach
    void setUp() {
        service = new TransferService(transferRepository, accountRepository, balanceHistoryRepository, auditLogService,
            transactionManager);
        userId = UUID.randomUUID();
        nubank = account("Nubank", "1234.56");
        inter = account("Inter", "600.00");
    }

    private Account account(String name, String balance) {
        Account a = new Account(userId, name, null, Account.AccountType.CHECKING, new BigDecimal(balance));
        a.setId(UUID.randomUUID());
        return a;
    }

    private CreateTransferRequest request(UUID id, UUID from, UUID to, BigDecimal amount) {
        return new CreateTransferRequest(id, from, to, amount, LocalDate.parse("2026-11-03"), "  Reserva ");
    }

    private CreateTransferRequest request(UUID from, UUID to, String amount) {
        return request(UUID.randomUUID(), from, to, new BigDecimal(amount));
    }

    private void stubSaves() {
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            t.setCreatedAt(OffsetDateTime.parse("2026-11-03T15:00:00Z"));
            return t;
        });
    }

    @Test
    void transferDebitsTheOriginCreditsTheDestinationAndRecordsEverything() {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.empty());
        when(accountRepository.findActiveByIdsForUpdate(List.of(nubank.getId(), inter.getId()), userId))
            .thenReturn(List.of(nubank, inter));
        stubSaves();

        TransferService.Result result = service.create(userId,
            request(transferId, nubank.getId(), inter.getId(), new BigDecimal("200")), "127.0.0.1");

        assertTrue(result.created());
        assertEquals(new BigDecimal("1034.56"), nubank.getCurrentBalance());
        assertEquals(new BigDecimal("800.00"), inter.getCurrentBalance());
        verify(accountRepository).save(nubank);
        verify(accountRepository).save(inter);

        ArgumentCaptor<AccountBalanceHistory> history = ArgumentCaptor.forClass(AccountBalanceHistory.class);
        verify(balanceHistoryRepository, times(2)).save(history.capture());
        assertEquals(nubank.getId(), history.getAllValues().get(0).getAccountId());
        assertEquals(new BigDecimal("1034.56"), history.getAllValues().get(0).getBalanceSnapshot());
        assertEquals(inter.getId(), history.getAllValues().get(1).getAccountId());
        assertEquals(new BigDecimal("800.00"), history.getAllValues().get(1).getBalanceSnapshot());
        assertTrue(history.getAllValues().stream()
            .allMatch(h -> AccountBalanceHistory.EVENT_TRANSFER.equals(h.getCreatedByEvent())
                && userId.equals(h.getUserId()) && transferId.equals(h.getTransferId())));

        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).saveAndFlush(saved.capture());
        Transfer transfer = saved.getValue();
        assertEquals(transferId, transfer.getId());
        assertTrue(transfer.isNew());
        assertEquals(userId, transfer.getUserId());
        assertEquals(new BigDecimal("200.00"), transfer.getAmount());
        assertEquals(LocalDate.parse("2026-11-03"), transfer.getTransferDate());
        assertEquals("Reserva", transfer.getDescription());

        verify(auditLogService).log(eq(TransferService.AUDIT_CREATED), eq(userId), eq("127.0.0.1"),
            eq("{\"id\":\"" + transferId + "\",\"amount\":200.00}"));

        TransferResponse response = result.transfer();
        assertEquals("Nubank", response.fromAccountName());
        assertEquals("Inter", response.toAccountName());
        assertEquals(new BigDecimal("200.00"), response.amount());
    }

    @Test
    void negativeResultingBalanceIsAllowed() {
        Account small = account("Nubank", "100.00");
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(small, inter));
        stubSaves();

        service.create(userId, request(small.getId(), inter.getId(), "150"), "127.0.0.1");

        assertEquals(new BigDecimal("-50.00"), small.getCurrentBalance());
        assertEquals(new BigDecimal("750.00"), inter.getCurrentBalance());
    }

    @Test
    void repeatedIdOfTheSameUserReturnsTheExistingTransferWithoutChangingAnything() {
        Transfer existing = new Transfer(UUID.randomUUID(), userId, nubank.getId(), inter.getId(),
            new BigDecimal("200.00"), LocalDate.parse("2026-11-03"), null);
        when(transferRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(accountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(nubank, inter));

        TransferService.Result result = service.create(userId,
            request(existing.getId(), nubank.getId(), inter.getId(), new BigDecimal("200")), "127.0.0.1");

        assertFalse(result.created());
        assertEquals(existing.getId(), result.transfer().id());
        assertEquals("Nubank", result.transfer().fromAccountName());
        assertEquals(new BigDecimal("1234.56"), nubank.getCurrentBalance());
        verify(accountRepository, never()).findActiveByIdsForUpdate(anyList(), any());
        verify(transferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(balanceHistoryRepository, auditLogService);
    }

    @Test
    void idOfAnotherUsersTransferIs404() {
        Transfer theirs = new Transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            BigDecimal.TEN, LocalDate.parse("2026-11-03"), null);
        when(transferRepository.findById(theirs.getId())).thenReturn(Optional.of(theirs));

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class, () -> service.create(userId,
            request(theirs.getId(), nubank.getId(), inter.getId(), BigDecimal.TEN), "127.0.0.1"));

        assertEquals(TransferService.NOT_FOUND_MESSAGE, e.getMessage());
        verify(transferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(balanceHistoryRepository, auditLogService);
    }

    @Test
    void lostPrimaryKeyRaceReReadsTheTransferThatWon() {
        UUID transferId = UUID.randomUUID();
        Transfer winner = new Transfer(transferId, userId, nubank.getId(), inter.getId(), new BigDecimal("200.00"),
            LocalDate.parse("2026-11-03"), null);
        // Not there when checked, there after the insert failed on the primary key
        when(transferRepository.findById(transferId)).thenReturn(Optional.empty(), Optional.of(winner));
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(nubank, inter));
        when(transferRepository.saveAndFlush(any(Transfer.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        when(accountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(nubank, inter));

        TransferService.Result result = service.create(userId,
            request(transferId, nubank.getId(), inter.getId(), new BigDecimal("200")), "127.0.0.1");

        assertFalse(result.created());
        assertEquals(transferId, result.transfer().id());
        verifyNoInteractions(balanceHistoryRepository, auditLogService);
    }

    @Test
    void integrityFailureWithoutAnExistingTransferIsRethrown() {
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(nubank, inter));
        when(transferRepository.saveAndFlush(any(Transfer.class)))
            .thenThrow(new DataIntegrityViolationException("value too long"));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.create(userId, request(nubank.getId(), inter.getId(), "10"), "127.0.0.1"));
    }

    @Test
    void sameAccountOnBothSidesIs400AndTouchesNothing() {
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> service.create(userId, request(nubank.getId(), nubank.getId(), "10"), "127.0.0.1"));

        assertEquals("Escolha contas diferentes", e.getMessage());
        verifyNoInteractions(accountRepository, balanceHistoryRepository, transferRepository, auditLogService);
    }

    @Test
    void invalidAmountsAre400WithoutValidationAndTouchNothing() {
        // Called without @Valid: null, zero, negative, 3 decimals or beyond DECIMAL(15,2) must not reach the database
        for (String amount : new String[] {null, "0", "0.00", "-10", "10.123", "10000000000000"}) {
            BigDecimal value = amount == null ? null : new BigDecimal(amount);
            BadRequestException e = assertThrows(BadRequestException.class, () -> service.create(userId,
                request(UUID.randomUUID(), nubank.getId(), inter.getId(), value), "127.0.0.1"), String.valueOf(amount));
            assertEquals(TransferService.INVALID_AMOUNT_MESSAGE, e.getMessage());
        }
        verifyNoInteractions(accountRepository, balanceHistoryRepository, transferRepository, auditLogService,
            transactionManager);
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreAccepted() {
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(nubank, inter));
        stubSaves();

        service.create(userId, request(nubank.getId(), inter.getId(), "10.500"), "127.0.0.1");

        assertEquals(new BigDecimal("1224.06"), nubank.getCurrentBalance());
    }

    @Test
    void missingIdsOrDateAre400() {
        CreateTransferRequest noId = new CreateTransferRequest(null, nubank.getId(), inter.getId(), BigDecimal.TEN,
            LocalDate.parse("2026-11-03"), null);
        CreateTransferRequest noFrom = new CreateTransferRequest(UUID.randomUUID(), null, inter.getId(), BigDecimal.TEN,
            LocalDate.parse("2026-11-03"), null);
        CreateTransferRequest noDate = new CreateTransferRequest(UUID.randomUUID(), nubank.getId(), inter.getId(),
            BigDecimal.TEN, null, null);

        for (CreateTransferRequest r : List.of(noId, noFrom, noDate)) {
            assertThrows(BadRequestException.class, () -> service.create(userId, r, "127.0.0.1"));
        }
        verifyNoInteractions(accountRepository, transferRepository);
    }

    @Test
    void accountOfAnotherUserOrRemovedIs404AndNoBalanceChanges() {
        // the locked lookup is scoped by user and deleted_at, so only one of the two comes back
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(nubank));

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
            () -> service.create(userId, request(nubank.getId(), UUID.randomUUID(), "10"), "127.0.0.1"));

        assertEquals("Conta não encontrada", e.getMessage());
        assertEquals(new BigDecimal("1234.56"), nubank.getCurrentBalance());
        verify(accountRepository, never()).save(any());
        verify(transferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(balanceHistoryRepository, auditLogService);
    }

    @Test
    void destinationBalanceBeyondDecimal15_2Is400() {
        Account rich = account("Rica", "9999999999999.00");
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(nubank, rich));

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> service.create(userId, request(nubank.getId(), rich.getId(), "1"), "127.0.0.1"));

        assertEquals(TransferService.BALANCE_OUT_OF_RANGE_MESSAGE, e.getMessage());
        assertEquals(new BigDecimal("1234.56"), nubank.getCurrentBalance());
        verify(accountRepository, never()).save(any());
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void originBalanceGoingPastTheNegativeLimitIs400AndNothingIsSaved() {
        Account overdrawn = account("No limite", "-9999999999999.00");
        when(accountRepository.findActiveByIdsForUpdate(anyList(), eq(userId))).thenReturn(List.of(overdrawn, inter));

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> service.create(userId, request(overdrawn.getId(), inter.getId(), "1.00"), "127.0.0.1"));

        assertEquals(TransferService.BALANCE_OUT_OF_RANGE_MESSAGE, e.getMessage());
        assertEquals(new BigDecimal("-9999999999999.00"), overdrawn.getCurrentBalance());
        assertEquals(new BigDecimal("600.00"), inter.getCurrentBalance());
        verify(accountRepository, never()).save(any());
        verify(transferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(balanceHistoryRepository, auditLogService);
    }

    @Test
    void listReturnsAtMost20WithTheNamesOfBothAccountsIncludingRemovedOnes() {
        nubank.setDeletedAt(OffsetDateTime.now());
        Transfer recent = new Transfer(UUID.randomUUID(), userId, nubank.getId(), inter.getId(),
            new BigDecimal("200.00"), LocalDate.parse("2026-11-03"), null);
        Transfer older = new Transfer(UUID.randomUUID(), userId, inter.getId(), nubank.getId(),
            new BigDecimal("50.00"), LocalDate.parse("2026-11-01"), "Volta");
        when(transferRepository.findActiveByUserId(eq(userId), any(Pageable.class))).thenReturn(List.of(recent, older));
        when(accountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(nubank, inter));

        List<TransferResponse> list = service.listRecent(userId);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transferRepository).findActiveByUserId(eq(userId), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(20, page.getValue().getPageSize());
        assertEquals(2, list.size());
        assertEquals("Nubank", list.get(0).fromAccountName());
        assertEquals("Inter", list.get(0).toAccountName());
        assertEquals("Inter", list.get(1).fromAccountName());
        assertEquals("Volta", list.get(1).description());
    }

    @Test
    void emptyListDoesNotLoadAccounts() {
        when(transferRepository.findActiveByUserId(eq(userId), any(Pageable.class))).thenReturn(List.of());

        assertEquals(List.of(), service.listRecent(userId));
        verifyNoInteractions(accountRepository);
    }
}
