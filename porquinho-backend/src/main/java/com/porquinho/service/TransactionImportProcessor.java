package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.entity.Transfer;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Background processing of an uploaded statement (Stories 5.3 and 5.4). Runs after the import row is committed,
 * outside the request thread, with the file content only in memory: detects the bank of the OFX (or takes it from the
 * CSV layout, Story 5.2), reads its entries ({@link OfxTransactionParser}, {@link CsvStatementReader}) and saves the
 * valid ones as PENDING together with the import counters, all in one database transaction. When the file cannot be
 * read or has no transactions the import becomes FAILED with a clear message and nothing is saved. Before saving, entries already in the target (duplicates) or matching an already
 * registered transfer are dropped and counted ({@link DuplicateDetector}, Story 5.5); when nothing is left the import
 * goes straight to COMPLETED (decision 1A of Story 5.5). Each saved entry is checked for Pix ({@link PixDetector},
 * Story 5.6). On a card, purchases with an installment marker join their purchase's group and create the future
 * installments, or confirm a future one saved before ({@link InstallmentPlanner}, Story 5.7). After the commit, so
 * with the advisory lock released, the rows are classified by the AI ({@link TransactionClassificationService}, Story
 * 5.8) and only then the import goes to REVIEW (decision 1A). Imports left PROCESSING with no processing running
 * (backend restarted, upload interrupted) are swept at startup and every 5 minutes: to REVIEW when their rows were
 * already saved, to FAILED when not. Logs carry only the import id, positions and reasons, never file content.
 *
 * <p>A separate bean so that {@code @Async} goes through the Spring proxy (a self-call would run synchronously).
 */
@Component
public class TransactionImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionImportProcessor.class);

    public static final String TRUNCATED_MESSAGE =
        "O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez.";
    public static final String UNEXPECTED_ERROR_MESSAGE = "Não foi possível processar o arquivo. Tente de novo.";
    public static final String INTERRUPTED_MESSAGE = "O processamento foi interrompido. Envie o arquivo de novo.";
    public static final String NO_TRANSACTIONS_MESSAGE =
        "Não encontramos transações neste arquivo. Exporte o extrato de novo e envie outra vez.";

    /** Audit event of the duplicate check (Story 5.5): only the import id and the counts, IP null (no request). */
    public static final String AUDIT_DUPLICATES_CHECKED = "import_duplicates_checked";

    /** A PROCESSING import with no change for this long has no processing running anymore. */
    static final Duration ORPHAN_AGE = Duration.ofMinutes(15);

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;
    private final AuditLogService auditLogService;
    private final TransactionClassificationService classificationService;
    private final BudgetBalanceCache budgetBalanceCache;
    private final DashboardCache dashboardCache;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    /** Taken when the bean is created, before the web server accepts uploads: older PROCESSING rows are orphans. */
    private final OffsetDateTime startedAt;

    @Autowired
    public TransactionImportProcessor(TransactionImportRepository transactionImportRepository,
                                      TransactionRepository transactionRepository,
                                      TransferRepository transferRepository, AuditLogService auditLogService,
                                      TransactionClassificationService classificationService,
                                      BudgetBalanceCache budgetBalanceCache, DashboardCache dashboardCache,
                                      PlatformTransactionManager transactionManager) {
        this(transactionImportRepository, transactionRepository, transferRepository, auditLogService,
            classificationService, budgetBalanceCache, dashboardCache, transactionManager, Clock.systemDefaultZone());
    }

    TransactionImportProcessor(TransactionImportRepository transactionImportRepository,
                               TransactionRepository transactionRepository, TransferRepository transferRepository,
                               AuditLogService auditLogService,
                               TransactionClassificationService classificationService,
                               BudgetBalanceCache budgetBalanceCache, DashboardCache dashboardCache,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.dashboardCache = dashboardCache;
        this.transactionImportRepository = transactionImportRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.auditLogService = auditLogService;
        this.classificationService = classificationService;
        this.budgetBalanceCache = budgetBalanceCache;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.startedAt = OffsetDateTime.now(clock);
    }

    /**
     * Processes the committed import {@code importId} of type {@code fileType}. Never throws: any failure becomes
     * FAILED with a generic message. The content is dropped when this returns.
     */
    @Async
    public void process(UUID importId, TransactionImport.FileType fileType, byte[] content) {
        try {
            String bank;
            if (fileType == TransactionImport.FileType.CSV) {
                // Story 5.2: the layout, already checked at upload (decision 2A), tells the bank
                Optional<CsvStatementReader.Layout> layout = CsvStatementReader.detect(content);
                if (layout.isEmpty()) {
                    log.info("Import {} failed: unknown CSV layout", importId);
                    transactionImportRepository.markFailed(importId, TransactionImportService.UNKNOWN_CSV_MESSAGE,
                        OffsetDateTime.now(clock));
                    return;
                }
                bank = layout.get().bankName();
            } else {
                if (OfxBankDetector.isTruncated(content)) {
                    log.info("Import {} failed: truncated OFX", importId);
                    transactionImportRepository.markFailed(importId, TRUNCATED_MESSAGE, OffsetDateTime.now(clock));
                    return;
                }
                bank = OfxBankDetector.detectBank(content);
            }
            int updated = transactionImportRepository.recordBank(importId, bank, OffsetDateTime.now(clock));
            if (updated == 0) {
                log.info("Import {} bank detected: {} (no longer processing)", importId, bank);
                return;
            }
            log.info("Import {} bank detected: {}", importId, bank);
            parseAndSave(importId, fileType, content);
        } catch (Exception e) {
            // only the exception type: a message could quote the file
            log.error("Import {} failed: unexpected {}", importId, e.getClass().getName());
            try {
                transactionImportRepository.markFailed(importId, UNEXPECTED_ERROR_MESSAGE, OffsetDateTime.now(clock));
            } catch (Exception again) {
                log.error("Import {} could not be marked as failed: {}", importId, again.getClass().getName());
            }
        }
    }

    /**
     * Stories 5.4, 5.2 and 5.5: reads the entries (OFX or CSV), drops the duplicates and the already registered
     * transfers, and saves the rest with the counters, all or nothing. Story 5.8: then classifies them.
     */
    private void parseAndSave(UUID importId, TransactionImport.FileType fileType, byte[] content) {
        TransactionImport transactionImport = transactionImportRepository.findById(importId).orElse(null);
        if (transactionImport == null) {
            log.info("Import {} no longer exists", importId);
            return;
        }
        boolean creditCard = transactionImport.getCreditCardId() != null;
        OfxTransactionParser.Result result = fileType == TransactionImport.FileType.CSV
            ? CsvStatementReader.parse(content, creditCard)
            : OfxTransactionParser.parse(content, creditCard);
        for (OfxTransactionParser.SkippedRow row : result.skippedRows()) {
            log.info("Import {} skipped row {}: {}", importId, row.position(), row.reason());
        }
        if (result.entries().isEmpty()) {
            log.info("Import {} failed: no transactions ({} skipped)", importId, result.skippedRows().size());
            transactionImportRepository.markFailed(importId, NO_TRANSACTIONS_MESSAGE, OffsetDateTime.now(clock));
            return;
        }
        int skipped = result.skippedRows().size();
        Outcome checked = transactionTemplate.execute(status -> {
            // Story 5.5: imports into the same target run one at a time from here to the commit, so each sees the rows
            // the other saved
            UUID targetId = TransactionCore.target(transactionImport.getCreditCardId(), transactionImport.getAccountId());
            // not core.lockTargets on purpose: one target, and the constructor stays as it is
            transactionImportRepository.lockTarget(TransactionCore.lockKey(targetId));
            DuplicateDetector.Result detection = detect(transactionImport, fileType, result.entries());
            List<Transaction> rows = detection.toSave().stream()
                .map(entry -> toTransaction(transactionImport, entry))
                .toList();
            // Story 5.7: card purchases become installments; a future one already saved is confirmed and counted as
            // a duplicate (decision 2A), so the counters still add up to the total
            InstallmentPlanner.Plan plan = creditCard
                ? InstallmentPlanner.plan(rows, transactionRepository.findActiveInstallmentsByCreditCard(
                    transactionImport.getUserId(), transactionImport.getCreditCardId()))
                : new InstallmentPlanner.Plan(rows, List.of(), 0, 0);
            int imported = rows.size() - plan.confirmed().size();
            int duplicates = detection.duplicates() + plan.confirmed().size();
            int total = result.totalRows();
            OffsetDateTime now = OffsetDateTime.now(clock);
            // the guarded update first: if the import already left PROCESSING nothing is written
            int updated = imported == 0
                ? transactionImportRepository.markCompletedWithNothingNew(importId, total, duplicates,
                    detection.transfers(), skipped, now)
                : transactionImportRepository.recordParsed(importId, total, imported, duplicates,
                    detection.transfers(), skipped, now);
            if (updated == 0) {
                // the planner already changed the confirmed futures (managed entities): the guarded update flushed
                // them, so the transaction has to be rolled back
                status.setRollbackOnly();
                return null;
            }
            if (!plan.confirmed().isEmpty()) {
                transactionRepository.saveAll(plan.confirmed());
                // a confirmed future now counts as spent and leaves the committed; evicted after the commit
                budgetBalanceCache.evict(transactionImport.getUserId());
            }
            if (!plan.toInsert().isEmpty()) {
                transactionRepository.saveAll(plan.toInsert());
            }
            if (!plan.confirmed().isEmpty() || !plan.toInsert().isEmpty()) {
                // Story 7.7: no HTTP request behind this write, so the interceptor does not see it; after the commit
                dashboardCache.evictAll(transactionImport.getUserId());
            }
            Outcome outcome = new Outcome(imported, duplicates, detection.transfers(), plan);
            auditLogService.log(AUDIT_DUPLICATES_CHECKED, transactionImport.getUserId(), null,
                auditMetadata(importId, total, skipped, outcome));
            return outcome;
        });
        if (checked == null) {
            log.info("Import {} parsed but no longer processing: nothing saved", importId);
        } else if (checked.imported() == 0) {
            log.info("Import {} completed with nothing new: {} duplicate(s), {} transfer(s), {} skipped row(s), "
                + "{} installment(s) confirmed", importId, checked.duplicates(), checked.transfers(), skipped,
                checked.plan().confirmed().size());
        } else {
            log.info("Import {} saved: {} transaction(s), {} duplicate(s), {} transfer(s), "
                + "{} skipped row(s), {} installment(s), {} future installment(s), {} confirmed", importId,
                checked.imported(), checked.duplicates(), checked.transfers(), skipped, checked.plan().installments(),
                checked.plan().futuresCreated(), checked.plan().confirmed().size());
            classifyAndFinish(importId, transactionImport.getUserId(), creditCard);
        }
    }

    /**
     * Story 5.8: the rows are committed, so nothing here can make the import FAILED (they would stay active and a new
     * upload would see them as duplicates). If the last update fails, the sweep moves the import to REVIEW.
     */
    private void classifyAndFinish(UUID importId, UUID userId, boolean creditCard) {
        try {
            classificationService.classify(importId, userId, creditCard);
            if (transactionImportRepository.markReadyForReview(importId, OffsetDateTime.now(clock)) == 0) {
                log.info("Import {} classified but no longer processing", importId);
            } else {
                log.info("Import {} ready for review", importId);
            }
        } catch (Exception e) {
            log.error("Import {} could not be moved to review: {}", importId, e.getClass().getName());
        }
    }

    /** What an import saved: the counters of the import row and the installment plan (Stories 5.5 and 5.7). */
    record Outcome(int imported, int duplicates, int transfers, InstallmentPlanner.Plan plan) {
    }

    /**
     * Story 5.5: one query for the target's active transactions and one for the account's active transfers, both
     * between the earliest file date minus 1 day and the latest plus 1 day, both in a fixed order (date, creation, id)
     * so that ties always pick the same candidate. The format of the imports the existing transactions came from feeds
     * decision 4A.
     */
    private DuplicateDetector.Result detect(TransactionImport transactionImport, TransactionImport.FileType fileType,
                                            List<OfxTransactionParser.Entry> entries) {
        LocalDate first = entries.get(0).date();
        LocalDate last = first;
        for (OfxTransactionParser.Entry entry : entries) {
            if (entry.date().isBefore(first)) {
                first = entry.date();
            }
            if (entry.date().isAfter(last)) {
                last = entry.date();
            }
        }
        LocalDate from = first.minusDays(DuplicateDetector.MAX_DAYS_APART);
        LocalDate to = last.plusDays(DuplicateDetector.MAX_DAYS_APART);
        UUID userId = transactionImport.getUserId();
        UUID accountId = transactionImport.getAccountId();
        List<Transaction> existing;
        List<Transfer> transfers;
        if (accountId != null) {
            existing = transactionRepository.findDuplicateCandidatesByAccountBetween(userId, accountId, from, to);
            transfers = transferRepository.findActiveByAccountBetween(userId, accountId, from, to);
        } else {
            existing = transactionRepository.findDuplicateCandidatesByCreditCardBetween(userId,
                transactionImport.getCreditCardId(), from, to);
            transfers = List.of();
            // Story 5.7: a future installment is confirmed by the installment planner, never dropped as a duplicate
            existing = existing.stream().filter(transaction -> !transaction.isFutureInstallment()).toList();
        }
        Set<UUID> importIds = new HashSet<>();
        for (Transaction transaction : existing) {
            if (transaction.getImportId() != null) {
                importIds.add(transaction.getImportId());
            }
        }
        Map<UUID, TransactionImport.FileType> importFileTypes = new HashMap<>();
        if (!importIds.isEmpty()) {
            for (TransactionImport origin : transactionImportRepository.findByUserIdAndIdIn(userId, importIds)) {
                importFileTypes.put(origin.getId(), origin.getFileType());
            }
        }
        return DuplicateDetector.detect(entries, fileType, existing, importFileTypes, transfers, accountId);
    }

    /** Only the import id and the counts: never descriptions nor file content. */
    static String auditMetadata(UUID importId, int total, int skipped, Outcome outcome) {
        return "{\"import_id\":\"" + importId + "\",\"total_transactions\":" + total
            + ",\"imported_transactions\":" + outcome.imported()
            + ",\"duplicates_found\":" + outcome.duplicates()
            + ",\"transfers_found\":" + outcome.transfers()
            + ",\"skipped_rows\":" + skipped
            + ",\"installments_found\":" + outcome.plan().installments()
            + ",\"future_installments_created\":" + outcome.plan().futuresCreated()
            + ",\"installments_confirmed\":" + outcome.plan().confirmed().size() + "}";
    }

    private static Transaction toTransaction(TransactionImport transactionImport, OfxTransactionParser.Entry entry) {
        Transaction transaction = new Transaction();
        transaction.setUserId(transactionImport.getUserId());
        transaction.setAccountId(transactionImport.getAccountId());
        transaction.setCreditCardId(transactionImport.getCreditCardId());
        transaction.setImportId(transactionImport.getId());
        transaction.setExternalId(entry.externalId());
        transaction.setTransactionDate(entry.date());
        transaction.setDescription(entry.description());
        transaction.setOriginalDescription(entry.originalDescription());
        transaction.setAmount(entry.amount());
        transaction.setTransactionType(entry.type());
        transaction.setKind(entry.cardPayment() ? Transaction.Kind.CARD_PAYMENT : Transaction.Kind.REGULAR);
        transaction.setClassificationSource(Transaction.ClassificationSource.NONE);
        // Story 5.8: "Não classificado" from the start, so a row the AI never reaches needs no write; a card payment
        // does not go to the AI and has no confidence at all
        transaction.setClassificationConfidence(entry.cardPayment() ? null : 0);
        transaction.setReviewStatus(Transaction.ReviewStatus.PENDING);
        transaction.setAffectsBalance(false);
        // Story 5.6: after the duplicates, so the detector never changes how many rows are saved
        Optional<String> pixDetails = PixDetector.detect(entry.description(), entry.type());
        transaction.setPix(pixDetails.isPresent());
        transaction.setPixDetails(pixDetails.orElse(null));
        // Story 5.7: InstallmentPlanner fills the installment fields of card purchases
        transaction.setInstallment(false);
        transaction.setFutureInstallment(false);
        return transaction;
    }

    /** At startup: every import still PROCESSING with no change since before this backend came up has no processing. */
    @EventListener(ApplicationReadyEvent.class)
    public void failImportsInterruptedByRestart() {
        sweepOrphans(startedAt, "interrupted by restart");
    }

    /** Every 5 minutes: imports PROCESSING with no change for more than 15 minutes (processing lost or stuck). */
    @Scheduled(fixedRate = 300_000, initialDelay = 300_000)
    public void failStaleImports() {
        sweepOrphans(OffsetDateTime.now(clock).minus(ORPHAN_AGE), "stale");
    }

    private void sweepOrphans(OffsetDateTime updatedBefore, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int reviewed = transactionImportRepository.reviewOrphansWithRows(updatedBefore, now);
        int failed = transactionImportRepository.failOrphansWithoutRows(updatedBefore, INTERRUPTED_MESSAGE, now);
        if (reviewed + failed > 0) {
            log.info("{} import(s): {} moved to review, {} marked as failed", reason, reviewed, failed);
        }
    }
}
