package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.OnboardingProgress;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    @Mock
    private OnboardingProgressRepository onboardingProgressRepository;

    @Mock
    private AuditLogService auditLogService;

    private AccountService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, balanceHistoryRepository,
            onboardingProgressRepository, auditLogService);
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

    private Account active(String name) {
        Account account = new Account(userId, name, null, Account.AccountType.CHECKING, BigDecimal.TEN);
        account.setId(UUID.randomUUID());
        return account;
    }

    private void onboardingCompleted(boolean completed) {
        OnboardingProgress progress = new OnboardingProgress(userId);
        progress.setCompleted(completed);
        when(onboardingProgressRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
    }

    @Test
    void deleteSoftDeletesTheUsersAccountFillsDeletedByAndAudits() {
        Account bb = active("BB");
        Account nubank = active("Nubank");
        when(accountRepository.findActiveByUserIdForUpdate(userId)).thenReturn(List.of(bb, nubank));

        service.delete(userId, bb.getId(), "10.0.0.1");

        assertNotNull(bb.getDeletedAt());
        assertEquals(userId, bb.getDeletedBy());
        assertNull(nubank.getDeletedAt());
        verify(accountRepository).save(bb);
        verify(accountRepository, never()).delete(any());
        verify(auditLogService).log("account_deleted", userId, "10.0.0.1", "{\"id\":\"" + bb.getId() + "\"}");
        verifyNoInteractions(balanceHistoryRepository);
    }

    @Test
    void deletingTheLastActiveAccountAfterTheOnboardingIsAConflict() {
        Account bb = active("BB");
        when(accountRepository.findActiveByUserIdForUpdate(userId)).thenReturn(List.of(bb));
        onboardingCompleted(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.delete(userId, bb.getId(), "ip"));

        assertEquals("Mantenha ao menos uma conta ativa", ex.getMessage());
        // Same lock order as OnboardingService.applyAccounts: onboarding progress first, then the accounts
        var order = inOrder(onboardingProgressRepository, accountRepository);
        order.verify(onboardingProgressRepository).findByUserIdForUpdate(userId);
        order.verify(accountRepository).findActiveByUserIdForUpdate(userId);
        assertNull(bb.getDeletedAt());
        assertNull(bb.getDeletedBy());
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deletingTheLastActiveAccountDuringTheOnboardingIsAllowed() {
        Account bb = active("BB");
        when(accountRepository.findActiveByUserIdForUpdate(userId)).thenReturn(List.of(bb));
        onboardingCompleted(false);

        service.delete(userId, bb.getId(), "ip");

        assertNotNull(bb.getDeletedAt());
        verify(auditLogService).log(eq("account_deleted"), eq(userId), eq("ip"), anyString());
    }

    @Test
    void deletingTheLastActiveAccountWithoutOnboardingProgressIsAllowed() {
        Account bb = active("BB");
        when(accountRepository.findActiveByUserIdForUpdate(userId)).thenReturn(List.of(bb));
        when(onboardingProgressRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        service.delete(userId, bb.getId(), "ip");

        assertNotNull(bb.getDeletedAt());
    }

    @Test
    void deletingSomeoneElsesMissingOrRemovedAccountIsNotFound() {
        when(accountRepository.findActiveByUserIdForUpdate(userId)).thenReturn(List.of(active("BB")));

        assertThrows(ResourceNotFoundException.class, () -> service.delete(userId, UUID.randomUUID(), "ip"));
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void listDeletedReturnsTheRemovedAccounts() {
        List<Account> removed = List.of(active("BB"));
        when(accountRepository.findDeletedByUserId(userId)).thenReturn(removed);

        assertSame(removed, service.listDeleted(userId));
    }

    @Test
    void restoreClearsDeletedAtAndDeletedByAndAudits() {
        Account bb = active("BB");
        bb.setDeletedAt(OffsetDateTime.now());
        bb.setDeletedBy(userId);
        when(accountRepository.findDeletedByIdForUpdate(bb.getId(), userId)).thenReturn(Optional.of(bb));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account restored = service.restore(userId, bb.getId(), "ip");

        assertSame(bb, restored);
        assertNull(restored.getDeletedAt());
        assertNull(restored.getDeletedBy());
        verify(auditLogService).log("account_restored", userId, "ip", "{\"id\":\"" + bb.getId() + "\"}");
        verifyNoInteractions(balanceHistoryRepository);
    }

    @Test
    void restoringAnActiveSomeoneElsesOrMissingAccountIsNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findDeletedByIdForUpdate(accountId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.restore(userId, accountId, "ip"));
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    private AccountBalanceHistory savedSnapshot() {
        ArgumentCaptor<AccountBalanceHistory> captor = ArgumentCaptor.forClass(AccountBalanceHistory.class);
        verify(balanceHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private Account existing(UUID accountId, String balance) {
        Account account = new Account(userId, "Nubank", "Nubank", Account.AccountType.CHECKING, new BigDecimal(balance));
        account.setId(accountId);
        when(accountRepository.findActiveByIdForUpdate(accountId, userId)).thenReturn(Optional.of(account));
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
        when(accountRepository.findActiveByIdForUpdate(accountId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(userId, accountId,
            new UpdateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE)));
        verify(accountRepository, never()).save(any());
        verify(balanceHistoryRepository, never()).save(any());
    }
}
