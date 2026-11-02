package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.exception.ResourceNotFoundException;
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

    private AccountService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository);
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
}
