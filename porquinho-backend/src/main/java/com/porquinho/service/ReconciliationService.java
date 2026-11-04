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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reconciliation of an account's balance with the bank (Story 4.6). In one transaction, with the account row locked:
 * the difference (bank balance - current balance) is computed here, never trusted from the client; the current
 * balance becomes the bank balance; last_reconciliation_date is set; the event goes to reconciliation_history; a
 * "reconciliation" snapshot goes to account_balance_history only when the balance changed; and the
 * "account_reconciled" audit is logged. A zero difference is accepted (the user checked and it matched).
 * Decision A: no adjustment transaction; the event is the record of the adjustment.
 * The user id always comes from the JWT; another user's or a removed account behaves as if it did not exist.
 */
@Service
public class ReconciliationService {

    public static final String AUDIT_RECONCILED = "account_reconciled";
    public static final String INVALID_BALANCE_MESSAGE =
        "Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula";
    public static final String REQUIRED_BALANCE_MESSAGE = "Informe o saldo no banco";
    public static final String DIFFERENCE_OUT_OF_RANGE_MESSAGE =
        "A diferença entre os saldos passa do limite permitido";

    /** The account's reconciliation list shows at most this many. */
    public static final int RECENT_LIMIT = 20;

    /** Largest absolute value that fits DECIMAL(15,2). */
    private static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");

    private final ReconciliationRepository reconciliationRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceHistoryRepository balanceHistoryRepository;
    private final AuditLogService auditLogService;

    public ReconciliationService(ReconciliationRepository reconciliationRepository, AccountRepository accountRepository,
                                 AccountBalanceHistoryRepository balanceHistoryRepository,
                                 AuditLogService auditLogService) {
        this.reconciliationRepository = reconciliationRepository;
        this.accountRepository = accountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Reconciles the account. 400 when the bank balance is missing or invalid (checked here too, so a caller without
     * bean validation gets 400 and not 500) or when the difference would not fit DECIMAL(15,2); 404
     * "Conta não encontrada" when the account is not one of the user's active accounts, with nothing changed.
     */
    @Transactional
    public ReconciliationResponse.Created reconcile(UUID userId, UUID accountId, CreateReconciliationRequest request,
                                                    String ipAddress) {
        BigDecimal bankBalance = validBankBalance(request);
        Account account = accountRepository.findActiveByIdForUpdate(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(AccountService.NOT_FOUND_MESSAGE));

        BigDecimal previous = account.getCurrentBalance();
        BigDecimal difference = bankBalance.subtract(previous);
        if (difference.abs().compareTo(MAX_BALANCE) > 0) {
            throw new BadRequestException(DIFFERENCE_OUT_OF_RANGE_MESSAGE, "bankBalance");
        }

        OffsetDateTime now = OffsetDateTime.now();
        account.setCurrentBalance(bankBalance);
        account.setLastReconciliationDate(now);
        Account saved = accountRepository.save(account);
        // Flushed here: a database failure surfaces inside the transaction and rolls everything back
        Reconciliation reconciliation = reconciliationRepository.saveAndFlush(new Reconciliation(accountId, userId, previous,
            bankBalance, difference, request.reason(), now));
        if (difference.signum() != 0) {
            AccountBalanceHistory snapshot = new AccountBalanceHistory(accountId, userId, bankBalance,
                AccountBalanceHistory.EVENT_RECONCILIATION);
            snapshot.setSnapshotDate(now);
            balanceHistoryRepository.save(snapshot);
        }
        auditLogService.log(AUDIT_RECONCILED, userId, ipAddress, auditMetadata(accountId, difference));
        return ReconciliationResponse.Created.of(saved, reconciliation);
    }

    /** The account's 20 most recent reconciliations; 404 when it is not one of the user's active accounts. */
    @Transactional(readOnly = true)
    public List<ReconciliationResponse> listRecent(UUID userId, UUID accountId) {
        if (accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId).isEmpty()) {
            throw new ResourceNotFoundException(AccountService.NOT_FOUND_MESSAGE);
        }
        return reconciliationRepository.findRecentByAccountId(accountId, userId, PageRequest.of(0, RECENT_LIMIT))
            .stream().map(ReconciliationResponse::of).toList();
    }

    /** Required, at most 2 decimals and fitting DECIMAL(15,2); returns it with scale 2. */
    private static BigDecimal validBankBalance(CreateReconciliationRequest request) {
        if (request == null || request.bankBalance() == null) {
            throw new BadRequestException(REQUIRED_BALANCE_MESSAGE, "bankBalance");
        }
        BigDecimal balance = request.bankBalance();
        if (balance.stripTrailingZeros().scale() > 2 || balance.abs().compareTo(MAX_BALANCE) > 0) {
            throw new BadRequestException(INVALID_BALANCE_MESSAGE, "bankBalance");
        }
        return balance.setScale(2, RoundingMode.UNNECESSARY);
    }

    static String auditMetadata(UUID accountId, BigDecimal difference) {
        return "{\"id\":\"" + accountId + "\",\"difference\":" + difference.toPlainString() + "}";
    }
}
