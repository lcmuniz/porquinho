package com.porquinho.service;

import com.porquinho.dto.TransactionResponse;
import com.porquinho.dto.TransactionUpdateRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The confirmed transactions of /transacoes (Story 5.12): list one month, edit, remove (soft delete) and remove the
 * future installments of a purchase. Only the user's CONFIRMED, active, non-future rows; anything else is 404 with
 * nothing written. Every change of balance follows decision D3 through {@link BalanceHistoryRecorder}: the old effect
 * of the row (if it is in the balance and still after the reference date of its account) is undone and the new one
 * (destination is an account and the new date is after its reference) applied, one snapshot per account that changed.
 * Lock order, as in an import: advisory lock of each target (key order), the transaction row, then the account rows
 * (id order). Logs and audit carry only ids and counts.
 */
@Service
public class TransactionService {

    public static final String AUDIT_EDITED = "transaction_edited";
    public static final String AUDIT_DELETED = "transaction_deleted";
    public static final String AUDIT_FUTURES_REMOVED = "installment_futures_removed";

    public static final String NOT_FOUND_MESSAGE = "Transação não encontrada";
    public static final String ONE_TARGET_MESSAGE = "Escolha uma conta ou um cartão";
    public static final String INVALID_MONTH_MESSAGE = "Mês inválido: use o formato AAAA-MM";
    public static final String CHANGED_MESSAGE = "Esta transação acabou de ser alterada. Recarregue a página e tente de novo.";
    public static final String AMOUNT_MESSAGE = "Informe um valor maior que zero";
    public static final String AMOUNT_OUT_OF_RANGE_MESSAGE = "Informe um valor de no máximo 13 dígitos antes da vírgula";
    public static final String INSTALLMENT_TARGET_MESSAGE =
        "Numa parcela, a conta ou o cartão e o tipo não podem ser alterados";
    public static final String BALANCE_OUT_OF_RANGE_MESSAGE =
        "Esta alteração deixaria o saldo da conta fora do limite permitido";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final TransactionRepository transactionRepository;
    private final TransactionImportRepository transactionImportRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final CategoryRepository categoryRepository;
    private final BalanceHistoryRecorder balanceHistoryRecorder;
    private final ClassificationPatternService patternService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionImportRepository transactionImportRepository,
                              AccountRepository accountRepository, CreditCardRepository creditCardRepository,
                              CategoryRepository categoryRepository, BalanceHistoryRecorder balanceHistoryRecorder,
                              ClassificationPatternService patternService, AuditLogService auditLogService,
                              Clock clock) {
        this.transactionRepository = transactionRepository;
        this.transactionImportRepository = transactionImportRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.categoryRepository = categoryRepository;
        this.balanceHistoryRecorder = balanceHistoryRecorder;
        this.patternService = patternService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** The confirmed transactions of {@code month} (YYYY-MM; null = current month in São Paulo), newest first. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> list(UUID userId, String month) {
        YearMonth yearMonth;
        try {
            yearMonth = month == null || month.isBlank() ? YearMonth.now(clock.withZone(SAO_PAULO)) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_MONTH_MESSAGE);
        }
        List<Transaction> rows = transactionRepository.findConfirmedBetween(userId, yearMonth.atDay(1),
            yearMonth.atEndOfMonth());
        return toResponses(userId, rows);
    }

    /**
     * Edits date, description, amount, type, category and account or card of a confirmed transaction. An installment
     * keeps its card or account and its type (decision Q4); the other fields change on that row only.
     */
    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionUpdateRequest request, String ipAddress) {
        if ((request.accountId() == null) == (request.creditCardId() == null)) {
            throw new BadRequestException(ONE_TARGET_MESSAGE);
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new BadRequestException(AMOUNT_MESSAGE);
        }
        if (!BalanceHistoryRecorder.fits(request.amount())) {
            throw new BadRequestException(AMOUNT_OUT_OF_RANGE_MESSAGE);
        }
        UUID newTarget = request.creditCardId() != null ? request.creditCardId() : request.accountId();
        Transaction row = lockRow(userId, id, newTarget);
        Transaction.Type newType = Transaction.Type.valueOf(request.type());
        if (row.isInstallment() && (row.getTransactionType() != newType
            || !Objects.equals(row.getAccountId(), request.accountId())
            || !Objects.equals(row.getCreditCardId(), request.creditCardId()))) {
            throw new BadRequestException(INSTALLMENT_TARGET_MESSAGE);
        }
        // an unchanged card may have been removed since: accepted as it is
        if (request.creditCardId() != null && !request.creditCardId().equals(row.getCreditCardId())) {
            creditCardRepository.findActiveByIdForUpdate(request.creditCardId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(CreditCardService.NOT_FOUND_MESSAGE));
        }
        boolean categoryChanged = !Objects.equals(row.getCategoryId(), request.categoryId());
        if (categoryChanged && request.categoryId() != null) {
            Category category = categoryRepository.findActiveByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(CategoryService.NOT_FOUND_MESSAGE));
            if (category.getParentCategoryId() == null) {
                throw new BadRequestException(TransactionImportReviewService.GROUP_NOT_ALLOWED_MESSAGE);
            }
        }

        Map<UUID, Account> accounts = lockAccounts(userId, row.getAccountId(), request.accountId());
        // an unchanged account may have been removed since: accepted, with no effect on its balance
        if (request.accountId() != null && !request.accountId().equals(row.getAccountId())
            && !accounts.containsKey(request.accountId())) {
            throw new ResourceNotFoundException(AccountService.NOT_FOUND_MESSAGE);
        }
        Map<UUID, BigDecimal> deltas = new HashMap<>();
        undoOldEffect(row, accounts, deltas);
        Account newAccount = request.accountId() == null ? null : accounts.get(request.accountId());
        boolean affects = false;
        if (newAccount != null && request.date().isAfter(balanceHistoryRecorder.referenceDate(newAccount))) {
            deltas.merge(newAccount.getId(), signed(newType, request.amount()), BigDecimal::add);
            affects = true;
        }
        int accountsChanged = applyDeltas(accounts, deltas);

        // decision Q5: learns the bank's text, so before the row changes (the JPQL read would flush the new description)
        if (categoryChanged && request.categoryId() != null) {
            patternService.learn(userId, List.of(id), request.categoryId());
        }
        row.setTransactionDate(request.date());
        row.setDescription(request.description());
        row.setAmount(request.amount());
        row.setTransactionType(newType);
        row.setAccountId(request.accountId());
        row.setCreditCardId(request.creditCardId());
        row.setAffectsBalance(affects);
        if (categoryChanged) {
            row.setCategoryId(request.categoryId());
            // no category is "Não classificado", not a choice with confidence
            row.setClassificationSource(request.categoryId() == null ? Transaction.ClassificationSource.NONE
                : Transaction.ClassificationSource.USER);
            row.setClassificationConfidence(request.categoryId() == null ? 0
                : TransactionImportReviewService.USER_CONFIDENCE);
        }
        transactionRepository.saveAndFlush(row);
        auditLogService.log(AUDIT_EDITED, userId, ipAddress, "{\"transaction_id\":\"" + id
            + "\",\"category_changed\":" + categoryChanged + ",\"accounts_changed\":" + accountsChanged + "}");
        return toResponses(userId, List.of(row)).get(0);
    }

    /**
     * Soft-deletes a confirmed transaction, taking its effect out of the balance (decision D3). The last confirmed
     * installment of a purchase takes its active futures along and marks the group (decision Q3).
     */
    @Transactional
    public void delete(UUID userId, UUID id, String ipAddress) {
        Transaction row = lockRow(userId, id, null);
        Map<UUID, Account> accounts = lockAccounts(userId, row.getAccountId(), null);
        Map<UUID, BigDecimal> deltas = new HashMap<>();
        undoOldEffect(row, accounts, deltas);
        int accountsChanged = applyDeltas(accounts, deltas);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int futuresRemoved = 0;
        UUID groupId = row.getInstallmentGroupId();
        if (groupId != null && transactionRepository.countOtherConfirmedInGroup(userId, groupId, id) == 0) {
            futuresRemoved = removeFutures(userId, groupId, now);
            row = transactionRepository.findById(id).orElseThrow(TransactionService::notFound);
        }
        row.setAffectsBalance(false);
        row.setDeletedAt(now);
        transactionRepository.save(row);
        auditLogService.log(AUDIT_DELETED, userId, ipAddress, "{\"transaction_id\":\"" + id
            + "\",\"accounts_changed\":" + accountsChanged + ",\"removed_installments\":" + futuresRemoved + "}");
    }

    /**
     * Soft-deletes the active future installments of the purchase of transaction {@code id} and marks its group, so
     * that no import creates them again (decision Q2). Returns how many were removed; 0 when there were none.
     */
    @Transactional
    public int removeFutureInstallments(UUID userId, UUID id, String ipAddress) {
        Transaction row = lockRow(userId, id, null);
        UUID groupId = row.getInstallmentGroupId();
        if (groupId == null) {
            return 0;
        }
        int removed = removeFutures(userId, groupId, OffsetDateTime.now(clock));
        auditLogService.log(AUDIT_FUTURES_REMOVED, userId, ipAddress, "{\"transaction_id\":\"" + id
            + "\",\"installment_group_id\":\"" + groupId + "\",\"removed_installments\":" + removed + "}");
        return removed;
    }

    /** Soft-deletes the group's active futures and marks its active rows (decision Q2). Clears the persistence context. */
    private int removeFutures(UUID userId, UUID groupId, OffsetDateTime now) {
        int removed = transactionRepository.softDeleteFutures(userId, groupId, now);
        transactionRepository.markFuturesRemoved(userId, groupId, now);
        return removed;
    }

    /**
     * Takes the advisory locks of the row's target and of {@code newTarget} in key order (like an import of either),
     * then locks the row. A row moved by a concurrent edit between the two reads is 409.
     */
    private Transaction lockRow(UUID userId, UUID id, UUID newTarget) {
        UUID oldTarget = transactionRepository.findEditableTarget(id, userId).orElseThrow(TransactionService::notFound);
        Set<Long> keys = new TreeSet<>();
        keys.add(TransactionImportProcessor.lockKey(oldTarget));
        if (newTarget != null) {
            keys.add(TransactionImportProcessor.lockKey(newTarget));
        }
        keys.forEach(transactionImportRepository::lockTarget);
        Transaction row = transactionRepository.findEditableForUpdate(id, userId).orElseThrow(TransactionService::notFound);
        UUID target = row.getCreditCardId() != null ? row.getCreditCardId() : row.getAccountId();
        if (!oldTarget.equals(target)) {
            throw new ConflictException(CHANGED_MESSAGE);
        }
        return row;
    }

    /** The user's active accounts among {@code ids} (nulls ignored), row-locked in id order. */
    private Map<UUID, Account> lockAccounts(UUID userId, UUID... ids) {
        List<UUID> wanted = Arrays.stream(ids).filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findActiveByIdsForUpdate(wanted, userId).stream()
            .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    /**
     * The old effect leaves the balance when the row is in it and its date is still after the reference of its account.
     * A removed account keeps its balance as it is.
     */
    private void undoOldEffect(Transaction row, Map<UUID, Account> accounts, Map<UUID, BigDecimal> deltas) {
        Account old = row.getAccountId() == null ? null : accounts.get(row.getAccountId());
        if (row.isAffectsBalance() && old != null
            && row.getTransactionDate().isAfter(balanceHistoryRecorder.referenceDate(old))) {
            deltas.merge(old.getId(), signed(row.getTransactionType(), row.getAmount()).negate(), BigDecimal::add);
        }
    }

    /** Applies every non-zero delta, all checked before any is written. Returns how many accounts changed. */
    private int applyDeltas(Map<UUID, Account> accounts, Map<UUID, BigDecimal> deltas) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        deltas.forEach((accountId, delta) -> {
            if (delta.signum() != 0) {
                BigDecimal balance = accounts.get(accountId).getCurrentBalance().add(delta);
                if (!BalanceHistoryRecorder.fits(balance)) {
                    throw new BadRequestException(BALANCE_OUT_OF_RANGE_MESSAGE);
                }
                balances.put(accountId, balance);
            }
        });
        balances.forEach((accountId, balance) -> {
            Account account = accounts.get(accountId);
            account.setCurrentBalance(balance);
            balanceHistoryRecorder.record(accountRepository.save(account), AccountBalanceHistory.EVENT_TRANSACTION);
        });
        return balances.size();
    }

    private static BigDecimal signed(Transaction.Type type, BigDecimal amount) {
        return type == Transaction.Type.CREDIT ? amount : amount.negate();
    }

    private List<TransactionResponse> toResponses(UUID userId, List<Transaction> rows) {
        Map<UUID, Category> categories = TransactionImportReviewService.categoriesOf(categoryRepository, userId, rows);
        Set<UUID> groups = rows.stream().map(Transaction::getInstallmentGroupId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, int[]> counts = new HashMap<>();
        if (!groups.isEmpty()) {
            for (Object[] count : transactionRepository.countActiveByGroup(userId, groups)) {
                counts.put((UUID) count[0], new int[] {((Number) count[1]).intValue(), ((Number) count[2]).intValue()});
            }
        }
        return rows.stream().map(row -> {
            int[] count = counts.getOrDefault(row.getInstallmentGroupId(), new int[2]);
            return new TransactionResponse(row.getId(), row.getTransactionDate(),
                row.getDescription(), row.getAmount(), row.getTransactionType().name(), row.getKind().name(),
                TransactionImportReviewService.category(row.getCategoryId(), categories),
                row.getClassificationSource().name(), row.getClassificationConfidence(), row.getReviewStatus().name(),
                row.isPix(), TransactionImportReviewService.pixDetails(row), row.isInstallment(),
                row.getInstallmentNumber(), row.getInstallmentTotal(), row.isFutureInstallment(), row.getAccountId(),
                row.getCreditCardId(), row.getInstallmentGroupId(), count[0], count[1]);
        }).toList();
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(NOT_FOUND_MESSAGE);
    }
}
