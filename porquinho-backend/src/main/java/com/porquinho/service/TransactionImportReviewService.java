package com.porquinho.service;

import com.porquinho.dto.ImportReviewActionResponse;
import com.porquinho.dto.ImportTransactionResponse;
import com.porquinho.dto.UpdateImportCategoryRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Review of an import: lists its rows, approves the high-confidence ones and discards it (Story 5.9), changes
 * categories and finishes it (Story 5.10). Only finishing touches balances. An import of another user is 404 with
 * nothing written; any of these actions outside REVIEW is 409, except finishing what is already COMPLETED, which
 * changes nothing. Logs and audit carry only ids and counts.
 */
@Service
public class TransactionImportReviewService {

    private static final Logger log = LoggerFactory.getLogger(TransactionImportReviewService.class);

    static final int MARK_IN_BALANCE_BATCH = 10_000;

    public static final String AUDIT_HIGH_CONFIDENCE_APPROVED = "import_high_confidence_approved";
    public static final String AUDIT_DISCARDED = "import_discarded";
    public static final String AUDIT_CATEGORY_CHANGED = "import_category_changed";
    public static final String AUDIT_COMPLETED = "import_completed";

    public static final String NOT_IN_REVIEW_MESSAGE = "Esta importação não está em revisão";
    public static final String TRANSACTION_NOT_FOUND_MESSAGE = "Transação não encontrada nesta revisão";
    public static final String ACCOUNT_REMOVED_MESSAGE =
        "A conta desta importação foi removida: restaure ou descarte a importação";
    public static final String CARD_REMOVED_MESSAGE =
        "O cartão desta importação foi removido: restaure ou descarte a importação";
    public static final String BALANCE_OUT_OF_RANGE_MESSAGE =
        "Concluir esta importação deixaria o saldo da conta fora do limite permitido";

    /** From here on the review shows a row as high confidence. */
    static final int HIGH_CONFIDENCE = 80;

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCore core;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final BalanceHistoryRecorder balanceHistoryRecorder;
    private final AuditLogService auditLogService;
    private final ClassificationPatternService patternService;

    public TransactionImportReviewService(TransactionImportRepository transactionImportRepository,
                                          TransactionRepository transactionRepository,
                                          TransactionCore core, AccountRepository accountRepository,
                                          CreditCardRepository creditCardRepository,
                                          BalanceHistoryRecorder balanceHistoryRecorder,
                                          AuditLogService auditLogService,
                                          ClassificationPatternService patternService) {
        this.transactionImportRepository = transactionImportRepository;
        this.transactionRepository = transactionRepository;
        this.core = core;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.balanceHistoryRecorder = balanceHistoryRecorder;
        this.auditLogService = auditLogService;
        this.patternService = patternService;
    }

    /** The rows of the import without the future installments (decision 2A), with their categories resolved. */
    @Transactional(readOnly = true)
    public List<ImportTransactionResponse> list(UUID userId, UUID importId) {
        transactionImportRepository.findByIdAndUserId(importId, userId).orElseThrow(TransactionImportReviewService::notFound);
        List<Transaction> rows = transactionRepository.findForReviewByImport(userId, importId);
        Map<UUID, Category> categories = core.categoriesOf(userId, rows);
        return rows.stream().map(row -> toResponse(row, categories)).toList();
    }

    /**
     * Marks the high-confidence rows as APPROVED (decision 1A). The import row stays locked meanwhile, so a discard at
     * the same time either comes after this or makes it 409. A second call changes nothing and audits nothing.
     */
    @Transactional
    public ImportReviewActionResponse approveHighConfidence(UUID userId, UUID importId, String ipAddress) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserIdForUpdate(importId, userId)
            .orElseThrow(TransactionImportReviewService::notFound);
        if (transactionImport.getStatus() != TransactionImport.Status.REVIEW) {
            throw new ConflictException(NOT_IN_REVIEW_MESSAGE);
        }
        int approved = transactionRepository.approveHighConfidence(userId, importId, HIGH_CONFIDENCE, OffsetDateTime.now());
        if (approved > 0) {
            auditLogService.log(AUDIT_HIGH_CONFIDENCE_APPROVED, userId, ipAddress,
                "{\"import_id\":\"" + importId + "\",\"approved_transactions\":" + approved + "}");
        }
        log.info("Import {}: {} high-confidence transaction(s) approved", importId, approved);
        return new ImportReviewActionResponse(importId, TransactionImport.Status.REVIEW.name(), approved);
    }

    /**
     * Sets the category the user chose on one or several rows of the review. The import row stays locked meanwhile, as
     * in {@link #approveHighConfidence}. A row that is not editable here makes the whole request 404, with nothing
     * written. The same category counts as reviewed (decision 2A).
     */
    @Transactional
    public ImportReviewActionResponse updateCategory(UUID userId, UUID importId, UpdateImportCategoryRequest request,
                                                     String ipAddress) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserIdForUpdate(importId, userId)
            .orElseThrow(TransactionImportReviewService::notFound);
        if (transactionImport.getStatus() != TransactionImport.Status.REVIEW) {
            throw new ConflictException(NOT_IN_REVIEW_MESSAGE);
        }
        Category category = core.requireChildCategory(userId, request.categoryId());
        Set<UUID> ids = new HashSet<>(request.transactionIds());
        if (transactionRepository.countEditable(userId, importId, ids) != ids.size()) {
            throw new ResourceNotFoundException(TRANSACTION_NOT_FOUND_MESSAGE);
        }
        CategoryChange change = writeCategory(userId, importId, ids, category.getId());
        // A discard of the confirming import may have made a row future again since the count: all or nothing
        if (change.rows() != ids.size()) {
            throw new ResourceNotFoundException(TRANSACTION_NOT_FOUND_MESSAGE);
        }
        auditLogService.log(AUDIT_CATEGORY_CHANGED, userId, ipAddress, "{\"import_id\":\"" + importId
            + "\",\"changed_transactions\":" + change.rows() + ",\"future_installments_changed\":" + change.futures()
            + "}");
        log.info("Import {}: category changed on {} transaction(s) and {} future installment(s)", importId, change.rows(),
            change.futures());
        return new ImportReviewActionResponse(importId, TransactionImport.Status.REVIEW.name(), change.rows());
    }

    /** The only place that writes a category chosen by the user, so the one Story 5.11 learns from. */
    private CategoryChange writeCategory(UUID userId, UUID importId, Set<UUID> ids, UUID categoryId) {
        OffsetDateTime now = OffsetDateTime.now();
        int rows = transactionRepository.applyUserCategory(userId, importId, ids, categoryId,
            TransactionCore.USER_CONFIDENCE, now);
        int futures = transactionRepository.applyUserCategoryToFutures(userId, importId, ids, categoryId,
            TransactionCore.USER_CONFIDENCE, now);
        patternService.learn(userId, ids, categoryId);
        return new CategoryChange(rows, futures);
    }

    /**
     * Finishes the import in one transaction: every row CONFIRMED and, in an import of an account, the balance effect of
     * decision D3. The lock order is the one of {@link #discard}: advisory lock of the target, import row, then the
     * account. Under the advisory lock a discard reads the right status of this import (5.9, decision 3A), and a second
     * call waits for the first and then finds it COMPLETED, which is a 200 that changes nothing.
     */
    @Transactional
    public ImportReviewActionResponse complete(UUID userId, UUID importId, String ipAddress) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserId(importId, userId)
            .orElseThrow(TransactionImportReviewService::notFound);
        UUID accountId = transactionImport.getAccountId();
        UUID cardId = transactionImport.getCreditCardId();
        core.lockTargets(TransactionCore.target(cardId, accountId));
        OffsetDateTime now = OffsetDateTime.now();
        if (transactionImportRepository.markCompleted(importId, now) == 0) {
            if (transactionImportRepository.findStatusById(importId) == TransactionImport.Status.COMPLETED) {
                return new ImportReviewActionResponse(importId, TransactionImport.Status.COMPLETED.name(), 0);
            }
            throw new ConflictException(NOT_IN_REVIEW_MESSAGE);
        }
        int affectingBalance = 0;
        if (cardId != null) {
            creditCardRepository.findActiveByIdForUpdate(cardId, userId)
                .orElseThrow(() -> new ConflictException(CARD_REMOVED_MESSAGE));
        } else {
            affectingBalance = applyBalanceEffect(userId, importId, accountId, now);
        }
        int confirmed = transactionRepository.confirmByImport(userId, importId, now);
        auditLogService.log(AUDIT_COMPLETED, userId, ipAddress, "{\"import_id\":\"" + importId
            + "\",\"confirmed_transactions\":" + confirmed + ",\"balance_transactions\":" + affectingBalance + "}");
        log.info("Import {} completed: {} transaction(s) confirmed, {} affecting the balance", importId, confirmed,
            affectingBalance);
        return new ImportReviewActionResponse(importId, TransactionImport.Status.COMPLETED.name(), confirmed);
    }

    /**
     * Decision D3: the rows dated after the reference date go into the balance, with one snapshot when the sum is not
     * zero. Throwing here rolls back the whole conclusion, so the import stays in REVIEW. Returns how many rows entered.
     */
    private int applyBalanceEffect(UUID userId, UUID importId, UUID accountId, OffsetDateTime now) {
        Account account = accountRepository.findActiveByIdForUpdate(accountId, userId)
            .orElseThrow(() -> new ConflictException(ACCOUNT_REMOVED_MESSAGE));
        Predicate<LocalDate> inBalance = core.inBalance(account);
        List<Transaction> rows = transactionRepository.findBalanceCandidates(userId, importId).stream()
            .filter(row -> inBalance.test(row.getTransactionDate()))
            .toList();
        BigDecimal effect = rows.stream().map(row -> TransactionCore.signed(row.getTransactionType(), row.getAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (effect.signum() != 0) {
            BigDecimal balance = account.getCurrentBalance().add(effect);
            if (!BalanceHistoryRecorder.fits(balance)) {
                throw new BadRequestException(BALANCE_OUT_OF_RANGE_MESSAGE);
            }
            account.setCurrentBalance(balance);
            balanceHistoryRecorder.record(accountRepository.save(account), AccountBalanceHistory.EVENT_TRANSACTION);
        }
        if (rows.isEmpty()) {
            return 0;
        }
        List<UUID> ids = rows.stream().map(Transaction::getId).toList();
        int marked = 0;
        // Postgres takes at most 32767 parameters per query; an upload is limited by size, not by rows
        for (int from = 0; from < ids.size(); from += MARK_IN_BALANCE_BATCH) {
            marked += transactionRepository.markInBalance(userId,
                ids.subList(from, Math.min(from + MARK_IN_BALANCE_BATCH, ids.size())), now);
        }
        return marked;
    }

    /**
     * Discards the import and soft-deletes its rows, future installments included, so they stop being candidates of the
     * duplicate check and of the installment groups. Under the advisory lock of the target, like the import itself: a
     * file of the same target being saved sees either all of this or none of it. Decision 3A: what another import
     * confirmed goes to it first, and what this one confirmed for another import goes back to being future.
     */
    @Transactional
    public ImportReviewActionResponse discard(UUID userId, UUID importId, String ipAddress) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserId(importId, userId)
            .orElseThrow(TransactionImportReviewService::notFound);
        core.lockTargets(TransactionCore.target(transactionImport.getCreditCardId(), transactionImport.getAccountId()));
        OffsetDateTime now = OffsetDateTime.now();
        if (transactionImportRepository.markDiscarded(importId, now) == 0) {
            throw new ConflictException(NOT_IN_REVIEW_MESSAGE);
        }
        int transferred = transactionRepository.transferConfirmedInstallments(userId, importId, now);
        int reverted = transactionRepository.revertConfirmedInstallments(userId, importId, now);
        int discarded = transactionRepository.softDeleteByImport(userId, importId, now);
        auditLogService.log(AUDIT_DISCARDED, userId, ipAddress, "{\"import_id\":\"" + importId
            + "\",\"discarded_transactions\":" + discarded + ",\"installments_transferred\":" + transferred
            + ",\"installments_reverted\":" + reverted + "}");
        log.info("Import {} discarded: {} transaction(s) removed, {} installment(s) transferred, {} reverted", importId,
            discarded, transferred, reverted);
        return new ImportReviewActionResponse(importId, TransactionImport.Status.DISCARDED.name(), discarded);
    }

    /** How many rows of the review and how many future installments got the category. */
    private record CategoryChange(int rows, int futures) {
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(TransactionImportService.NOT_FOUND_MESSAGE);
    }

    private static ImportTransactionResponse toResponse(Transaction row, Map<UUID, Category> categories) {
        return new ImportTransactionResponse(row.getId(), row.getTransactionDate(), row.getDescription(), row.getAmount(),
            row.getTransactionType().name(), row.getKind().name(),
            TransactionCore.category(row.getCategoryId(), categories), row.getClassificationSource().name(),
            row.getClassificationConfidence(), row.getReviewStatus().name(), row.isPix(), TransactionCore.pixDetails(row),
            row.isInstallment(), row.getInstallmentNumber(), row.getInstallmentTotal(), row.isFutureInstallment());
    }
}
