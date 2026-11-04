package com.porquinho.service;

import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.TransferResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.Transfer;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.TransferRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transfers between the user's own accounts (Story 4.5). In one transaction: the origin's current balance goes
 * down, the destination's goes up, each account gets a "transfer" row in account_balance_history with its new
 * balance and the transfer id, the transfer row is written and the "transfer_created" audit is logged. A negative
 * resulting balance is allowed (decision 1A of Story 3.2). Transfers do not touch categories or budgets.
 * The user id always comes from the JWT; another user's or a removed account behaves as if it did not exist.
 *
 * <p>Idempotent: the client sends the transfer id. Re-sending an id already used by the same user returns the
 * existing transfer with nothing changed; an id used by another user is 404. Two concurrent requests with the same
 * id race on the primary key: the loser's transaction rolls back and the existing row is re-read.
 */
@Service
public class TransferService {

    public static final String AUDIT_CREATED = "transfer_created";
    public static final String NOT_FOUND_MESSAGE = "Transferência não encontrada";
    public static final String BALANCE_OUT_OF_RANGE_MESSAGE =
        "A transferência deixaria o saldo de uma das contas fora do limite permitido";
    public static final String INVALID_REQUEST_MESSAGE = "Dados inválidos. Confira os campos e tente novamente.";
    public static final String INVALID_AMOUNT_MESSAGE =
        "Informe um valor maior que zero, com até 2 casas decimais e no máximo 13 dígitos antes da vírgula";

    /** The "Transferências recentes" list shows at most this many. */
    public static final int RECENT_LIMIT = 20;

    /** The transfer and whether this call created it (false: an earlier request with the same id did). */
    public record Result(TransferResponse transfer, boolean created) {
    }

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final BalanceHistoryRecorder balanceHistoryRecorder;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public TransferService(TransferRepository transferRepository, AccountRepository accountRepository,
                           BalanceHistoryRecorder balanceHistoryRecorder, AuditLogService auditLogService,
                           PlatformTransactionManager transactionManager) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.balanceHistoryRecorder = balanceHistoryRecorder;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
    }

    /**
     * Records the transfer, or returns the existing one for a repeated id. 400 when a field is missing or invalid
     * (checked here too, so a caller without bean validation gets 400 and not 500) or when both sides are the same
     * account; 404 "Conta não encontrada" when either account is not one of the user's active accounts, with no
     * balance changed. Both account rows are locked in id order in a single query, so two crossed transfers cannot
     * deadlock.
     */
    public Result create(UUID userId, CreateTransferRequest request, String ipAddress) {
        BigDecimal amount = validAmount(request);
        try {
            return transactionTemplate.execute(status -> createInTransaction(userId, request, amount, ipAddress));
        } catch (DataIntegrityViolationException e) {
            // Lost the race on the primary key against a request with the same id: that one committed first
            Result existing = readOnlyTemplate.execute(status -> existing(userId, request.id()).orElse(null));
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    private Result createInTransaction(UUID userId, CreateTransferRequest request, BigDecimal amount,
                                       String ipAddress) {
        Optional<Result> existing = existing(userId, request.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID fromId = request.fromAccountId();
        UUID toId = request.toAccountId();
        Map<UUID, Account> locked = byId(accountRepository.findActiveByIdsForUpdate(List.of(fromId, toId), userId));
        Account from = locked.get(fromId);
        Account to = locked.get(toId);
        if (from == null || to == null) {
            throw new ResourceNotFoundException(AccountService.NOT_FOUND_MESSAGE);
        }

        BigDecimal fromBalance = from.getCurrentBalance().subtract(amount);
        BigDecimal toBalance = to.getCurrentBalance().add(amount);
        if (!BalanceHistoryRecorder.fits(fromBalance) || !BalanceHistoryRecorder.fits(toBalance)) {
            throw new BadRequestException(BALANCE_OUT_OF_RANGE_MESSAGE);
        }

        // Inserted (and flushed) first: a repeated id fails here, and the snapshots reference the row
        Transfer transfer = transferRepository.saveAndFlush(new Transfer(request.id(), userId, fromId, toId, amount,
            request.transferDate(), request.description()));
        from.setCurrentBalance(fromBalance);
        to.setCurrentBalance(toBalance);
        accountRepository.save(from);
        accountRepository.save(to);
        balanceHistoryRecorder.recordTransfer(from, transfer.getId());
        balanceHistoryRecorder.recordTransfer(to, transfer.getId());
        auditLogService.log(AUDIT_CREATED, userId, ipAddress, auditMetadata(transfer));
        return new Result(TransferResponse.of(transfer, locked), true);
    }

    /** The transfer with this id, if the user already created it; 404 if the id belongs to another user. */
    private Optional<Result> existing(UUID userId, UUID transferId) {
        return transferRepository.findById(transferId).map(transfer -> {
            if (!transfer.getUserId().equals(userId)) {
                throw new ResourceNotFoundException(NOT_FOUND_MESSAGE);
            }
            Map<UUID, Account> accounts = byId(accountRepository.findByUserIdOrderByCreatedAtAsc(userId));
            return new Result(TransferResponse.of(transfer, accounts), false);
        });
    }

    /**
     * Same rules as the request validation, enforced here as well: ids and date present, different accounts, amount
     * > 0 with at most 2 decimals and fitting DECIMAL(15,2). Returns the amount with scale 2.
     */
    private static BigDecimal validAmount(CreateTransferRequest request) {
        if (request.id() == null || request.fromAccountId() == null || request.toAccountId() == null
            || request.transferDate() == null) {
            throw new BadRequestException(INVALID_REQUEST_MESSAGE);
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BadRequestException(CreateTransferRequest.SAME_ACCOUNT_MESSAGE);
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2
            || !BalanceHistoryRecorder.fits(amount)) {
            throw new BadRequestException(INVALID_AMOUNT_MESSAGE);
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** The user's 20 most recent active transfers, with the account names (removed accounts included). */
    @Transactional(readOnly = true)
    public List<TransferResponse> listRecent(UUID userId) {
        List<Transfer> transfers = transferRepository.findActiveByUserId(userId, PageRequest.of(0, RECENT_LIMIT));
        if (transfers.isEmpty()) {
            return List.of();
        }
        Map<UUID, Account> accounts = byId(accountRepository.findByUserIdOrderByCreatedAtAsc(userId));
        return transfers.stream().map(t -> TransferResponse.of(t, accounts)).toList();
    }

    private static Map<UUID, Account> byId(List<Account> accounts) {
        return accounts.stream().collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    static String auditMetadata(Transfer transfer) {
        return "{\"id\":\"" + transfer.getId() + "\",\"amount\":" + transfer.getAmount().toPlainString() + "}";
    }
}
