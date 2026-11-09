package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Account.AccountType;
import com.porquinho.entity.OnboardingProgress;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.exception.StaleDataMessages;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bank accounts (Story 3.2: create, list and remove; Story 4.3: edit; Story 4.4: audited removal, removed list
 * and restore; transfers come later in Epic 4).
 * The user id always comes from the JWT; another user's account behaves as if it did not exist.
 * Every change of the current balance made here is recorded in account_balance_history, in the same
 * transaction and through BalanceHistoryRecorder: "initial" on creation, "edit" when an edit changes the balance. Removing or restoring never touches
 * that history.
 */
@Service
public class AccountService {

    /** Created when the user skips step 2 of the onboarding without any account. */
    public static final String DEFAULT_ACCOUNT_NAME = "Minha Conta Principal";

    public static final String NOT_FOUND_MESSAGE = "Conta não encontrada";
    public static final String LAST_ACTIVE_MESSAGE = "Mantenha ao menos uma conta ativa";
    public static final String AUDIT_DELETED = "account_deleted";
    public static final String AUDIT_RESTORED = "account_restored";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceHistoryRecorder balanceHistoryRecorder;
    private final OnboardingProgressRepository onboardingProgressRepository;
    private final AuditLogService auditLogService;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                          BalanceHistoryRecorder balanceHistoryRecorder,
                          OnboardingProgressRepository onboardingProgressRepository, AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.balanceHistoryRecorder = balanceHistoryRecorder;
        this.onboardingProgressRepository = onboardingProgressRepository;
        this.auditLogService = auditLogService;
    }

    /** Creates an account; the current balance starts equal to the initial balance. */
    @Transactional
    public Account create(UUID userId, CreateAccountRequest request) {
        return create(userId, request.accountName(), request.bankName(),
            AccountType.valueOf(request.accountType()), request.initialBalance());
    }

    @Transactional
    public Account create(UUID userId, String accountName, String bankName, AccountType type, BigDecimal initialBalance) {
        BigDecimal balance = initialBalance.setScale(2, RoundingMode.UNNECESSARY);
        Account account = accountRepository.save(new Account(userId, accountName, bankName, type, balance));
        balanceHistoryRecorder.record(account, AccountBalanceHistory.EVENT_INITIAL);
        return account;
    }

    /**
     * Edits name, bank, type and current balance (Story 4.3); the initial balance never changes. 404 when the
     * account does not exist, belongs to someone else or was removed. A changed balance (compared by value,
     * so 100 and 100.00 are the same) writes one "edit" snapshot and absorbs the rows that were in the balance (retro Epic 5, X2); an unchanged balance writes none. 409 when the
     * version sent is not the current one (a transfer, reconciliation or edit happened after the form opened):
     * checked after the row lock, so nothing is written.
     */
    @Transactional
    public Account update(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account = accountRepository.findActiveByIdForUpdate(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        if (!Objects.equals(account.getVersion(), request.version())) {
            throw new ConflictException(StaleDataMessages.ACCOUNT);
        }
        BigDecimal balance = request.currentBalance().setScale(2, RoundingMode.UNNECESSARY);
        boolean balanceChanged = account.getCurrentBalance().compareTo(balance) != 0;
        account.setAccountName(request.accountName());
        account.setBankName(request.bankName());
        account.setAccountType(AccountType.valueOf(request.accountType()));
        account.setCurrentBalance(balance);
        Account saved = accountRepository.save(account);
        if (balanceChanged) {
            balanceHistoryRecorder.record(saved, AccountBalanceHistory.EVENT_EDIT);
            transactionRepository.absorbIntoBalance(userId, accountId, balanceHistoryRecorder.now());
        }
        return saved;
    }

    /** "Minha Conta Principal": checking, balance 0 (onboarding step 2 skipped with no account). */
    @Transactional
    public Account createDefault(UUID userId) {
        return create(userId, DEFAULT_ACCOUNT_NAME, null, AccountType.CHECKING, BigDecimal.ZERO);
    }

    /** The user's active accounts, oldest first. */
    @Transactional(readOnly = true)
    public List<Account> listActive(UUID userId) {
        return accountRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countActive(UUID userId) {
        return accountRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    /** The user's removed accounts, most recently removed first (Story 4.4). */
    @Transactional(readOnly = true)
    public List<Account> listDeleted(UUID userId) {
        return accountRepository.findDeletedByUserId(userId);
    }

    /**
     * Soft delete (Story 4.4): fills deleted_at and deleted_by and writes the "account_deleted" audit. 404 when the
     * account does not exist, belongs to someone else or was already removed. Once the onboarding is completed the
     * user must keep at least one active account (409); during step 2 every account may still be removed, since
     * skipping the step creates "Minha Conta Principal". Locks the onboarding progress first and then the active
     * accounts, so two concurrent removals cannot both pass the check. OnboardingService.applyAccounts takes the same
     * onboarding progress lock first (it does not lock the accounts), so a removal cannot interleave with completing
     * step 2.
     */
    @Transactional
    public void delete(UUID userId, UUID accountId, String ipAddress) {
        boolean onboardingCompleted = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .map(OnboardingProgress::isCompleted).orElse(false);
        List<Account> active = accountRepository.findActiveByUserIdForUpdate(userId);
        Account account = active.stream()
            .filter(a -> a.getId().equals(accountId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        if (active.size() <= 1 && onboardingCompleted) {
            throw new ConflictException(LAST_ACTIVE_MESSAGE);
        }
        account.setDeletedAt(OffsetDateTime.now());
        account.setDeletedBy(userId);
        accountRepository.save(account);
        auditLogService.log(AUDIT_DELETED, userId, ipAddress, idMetadata(accountId));
    }

    /**
     * Restore (Story 4.4): clears deleted_at and deleted_by and writes the "account_restored" audit. 404 when the
     * account does not exist, belongs to someone else or is not removed.
     */
    @Transactional
    public Account restore(UUID userId, UUID accountId, String ipAddress) {
        Account account = accountRepository.findDeletedByIdForUpdate(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        account.setDeletedAt(null);
        account.setDeletedBy(null);
        Account saved = accountRepository.save(account);
        auditLogService.log(AUDIT_RESTORED, userId, ipAddress, idMetadata(accountId));
        return saved;
    }

    static String idMetadata(UUID id) {
        return "{\"id\":\"" + id + "\"}";
    }
}
