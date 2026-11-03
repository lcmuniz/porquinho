package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Account.AccountType;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bank accounts (Story 3.2: create, list and remove; Story 4.3: edit; transfers come later in Epic 4).
 * The user id always comes from the JWT; another user's account behaves as if it did not exist.
 * Every change of the current balance made here is recorded in account_balance_history, in the same
 * transaction: "initial" on creation, "edit" when an edit changes the balance.
 */
@Service
public class AccountService {

    /** Created when the user skips step 2 of the onboarding without any account. */
    public static final String DEFAULT_ACCOUNT_NAME = "Minha Conta Principal";

    private final AccountRepository accountRepository;
    private final AccountBalanceHistoryRepository balanceHistoryRepository;

    public AccountService(AccountRepository accountRepository, AccountBalanceHistoryRepository balanceHistoryRepository) {
        this.accountRepository = accountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
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
        recordBalance(account, AccountBalanceHistory.EVENT_INITIAL);
        return account;
    }

    /**
     * Edits name, bank, type and current balance (Story 4.3); the initial balance never changes. 404 when the
     * account does not exist, belongs to someone else or was removed. A changed balance (compared by value,
     * so 100 and 100.00 are the same) writes one "edit" snapshot; an unchanged balance writes none.
     */
    @Transactional
    public Account update(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        BigDecimal balance = request.currentBalance().setScale(2, RoundingMode.UNNECESSARY);
        boolean balanceChanged = account.getCurrentBalance().compareTo(balance) != 0;
        account.setAccountName(request.accountName());
        account.setBankName(request.bankName());
        account.setAccountType(AccountType.valueOf(request.accountType()));
        account.setCurrentBalance(balance);
        Account saved = accountRepository.save(account);
        if (balanceChanged) {
            recordBalance(saved, AccountBalanceHistory.EVENT_EDIT);
        }
        return saved;
    }

    private void recordBalance(Account account, String event) {
        balanceHistoryRepository.save(
            new AccountBalanceHistory(account.getId(), account.getUserId(), account.getCurrentBalance(), event));
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

    /** Soft delete. 404 when the account does not exist, belongs to someone else or was already removed. */
    @Transactional
    public void delete(UUID userId, UUID accountId) {
        Account account = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        account.setDeletedAt(OffsetDateTime.now());
        accountRepository.save(account);
    }
}
