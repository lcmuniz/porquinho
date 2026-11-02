package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.Account.AccountType;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bank accounts (Story 3.2: create, list and remove; editing and transfers come in Epic 4).
 * The user id always comes from the JWT; another user's account behaves as if it did not exist.
 */
@Service
public class AccountService {

    /** Created when the user skips step 2 of the onboarding without any account. */
    public static final String DEFAULT_ACCOUNT_NAME = "Minha Conta Principal";

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
        return accountRepository.save(new Account(userId, accountName, bankName, type, balance));
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
