package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    private AccountService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, balanceHistoryRepository);
        userId = UUID.randomUUID();
    }

    private Account savedAccount() {
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createCopiesTheInitialBalanceToTheCurrentBalance() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateAccountRequest("  Nubank ", "  ", "CHECKING", new BigDecimal("1234.56")));

        Account saved = savedAccount();
        assertEquals(userId, saved.getUserId());
        assertEquals("Nubank", saved.getAccountName());
        assertNull(saved.getBankName());
        assertEquals(Account.AccountType.CHECKING, saved.getAccountType());
        assertEquals(new BigDecimal("1234.56"), saved.getInitialBalance());
        assertEquals(new BigDecimal("1234.56"), saved.getCurrentBalance());
        assertNull(saved.getDeletedAt());
    }

    @Test
    void unicodeSpacesAroundNamesAreStripped() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateAccountRequest("\u00a0Nubank\u00a0 ", "\u00a0\u00a0", "CHECKING", BigDecimal.ONE));

        Account saved = savedAccount();
        assertEquals("Nubank", saved.getAccountName());
        assertNull(saved.getBankName());
    }

    @Test
    void negativeBalanceIsAcceptedAndScaledToTwoDecimals() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, new CreateAccountRequest("Itaú", "Itaú", "SAVINGS", new BigDecimal("-150")));

        Account saved = savedAccount();
        assertEquals(new BigDecimal("-150.00"), saved.getInitialBalance());
        assertEquals(new BigDecimal("-150.00"), saved.getCurrentBalance());
        assertEquals(Account.AccountType.SAVINGS, saved.getAccountType());
    }

    @Test
    void createDefaultIsMinhaContaPrincipalCheckingWithZeroBalance() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createDefault(userId);

        Account saved = savedAccount();
        assertEquals("Minha Conta Principal", saved.getAccountName());
        assertEquals(Account.AccountType.CHECKING, saved.getAccountType());
        assertEquals(new BigDecimal("0.00"), saved.getInitialBalance());
        assertEquals(new BigDecimal("0.00"), saved.getCurrentBalance());
    }

    @Test
    void deleteSoftDeletesTheUsersAccount() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(userId, "Nubank", null, Account.AccountType.CHECKING, BigDecimal.TEN);
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)).thenReturn(Optional.of(account));

        service.delete(userId, accountId);

        assertNotNull(account.getDeletedAt());
        verify(accountRepository).save(account);
        verify(accountRepository, never()).delete(any());
    }

    @Test
    void deletingSomeoneElsesOrMissingAccountIsNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(userId, accountId));
        verify(accountRepository, never()).save(any());
    }

    private AccountBalanceHistory savedSnapshot() {
        ArgumentCaptor<AccountBalanceHistory> captor = ArgumentCaptor.forClass(AccountBalanceHistory.class);
        verify(balanceHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private Account existing(UUID accountId, String balance) {
        Account account = new Account(userId, "Nubank", "Nubank", Account.AccountType.CHECKING, new BigDecimal(balance));
        account.setId(accountId);
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        return account;
    }

    @Test
    void createRecordsAnInitialSnapshotWithTheInitialBalance() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(accountId);
            return a;
        });

        service.create(userId, new CreateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("-150")));

        AccountBalanceHistory snapshot = savedSnapshot();
        assertEquals(accountId, snapshot.getAccountId());
        assertEquals(userId, snapshot.getUserId());
        assertEquals(new BigDecimal("-150.00"), snapshot.getBalanceSnapshot());
        assertEquals("initial", snapshot.getCreatedByEvent());
    }

    @Test
    void createDefaultAlsoRecordsAnInitialSnapshot() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createDefault(userId);

        AccountBalanceHistory snapshot = savedSnapshot();
        assertEquals(new BigDecimal("0.00"), snapshot.getBalanceSnapshot());
        assertEquals("initial", snapshot.getCreatedByEvent());
    }

    @Test
    void updateChangesNameBankTypeAndCurrentBalanceButNotTheInitialBalance() {
        UUID accountId = UUID.randomUUID();
        Account account = existing(accountId, "100.00");

        Account updated = service.update(userId, accountId,
            new UpdateAccountRequest(" Nubank PJ ", "  ", "SAVINGS", new BigDecimal("250")));

        assertSame(account, updated);
        assertEquals("Nubank PJ", updated.getAccountName());
        assertNull(updated.getBankName());
        assertEquals(Account.AccountType.SAVINGS, updated.getAccountType());
        assertEquals(new BigDecimal("250.00"), updated.getCurrentBalance());
        assertEquals(new BigDecimal("100.00"), updated.getInitialBalance());
        verify(accountRepository).save(account);
    }

    @Test
    void updateWithANewBalanceRecordsOneEditSnapshot() {
        UUID accountId = UUID.randomUUID();
        existing(accountId, "100.00");

        service.update(userId, accountId, new UpdateAccountRequest("Nubank", "Nubank", "CHECKING", new BigDecimal("250.00")));

        AccountBalanceHistory snapshot = savedSnapshot();
        assertEquals(accountId, snapshot.getAccountId());
        assertEquals(userId, snapshot.getUserId());
        assertEquals(new BigDecimal("250.00"), snapshot.getBalanceSnapshot());
        assertEquals("edit", snapshot.getCreatedByEvent());
    }

    @Test
    void updateWithTheSameBalanceRecordsNoSnapshot() {
        UUID accountId = UUID.randomUUID();
        existing(accountId, "100.00");

        // 100 and 100.00 are the same balance
        service.update(userId, accountId, new UpdateAccountRequest("Nubank PJ", null, "CHECKING", new BigDecimal("100")));

        verify(balanceHistoryRepository, never()).save(any());
    }

    @Test
    void updatingSomeoneElsesMissingOrRemovedAccountIsNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(userId, accountId,
            new UpdateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE)));
        verify(accountRepository, never()).save(any());
        verify(balanceHistoryRepository, never()).save(any());
    }
}
