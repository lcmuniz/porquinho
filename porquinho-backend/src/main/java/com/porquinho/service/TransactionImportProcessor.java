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
 * valid ones as PENDING together with the import counters, moving the import to REVIEW (decision 1A), all in one
 * database transaction. When the file cannot be read or has no transactions the import becomes FAILED with a clear
 * message and nothing is saved. Before saving, entries already in the target (duplicates) or matching an already
 * registered transfer are dropped and counted ({@link DuplicateDetector}, Story 5.5); when nothing is left the import
 * goes straight to COMPLETED (decision 1A of Story 5.5). Each saved entry is checked for Pix ({@link PixDetector},
 * Story 5.6). Imports left PROCESSING with no processing running (backend restarted, upload interrupted) are failed at
 * startup and every 5 minutes. Logs carry only the import id, positions and reasons, never file content.
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

    /** A PROCESSING import older than this has no processing running anymore. */
    static final Duration ORPHAN_AGE = Duration.ofMinutes(15);

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    /** Taken when the bean is created, before the web server accepts uploads: older PROCESSING rows are orphans. */
    private final OffsetDateTime startedAt;

    @Autowired
    public TransactionImportProcessor(TransactionImportRepository transactionImportRepository,
                                      TransactionRepository transactionRepository,
                                      TransferRepository transferRepository, AuditLogService auditLogService,
                                      PlatformTransactionManager transactionManager) {
        this(transactionImportRepository, transactionRepository, transferRepository, auditLogService,
            transactionManager, Clock.systemDefaultZone());
    }

    TransactionImportProcessor(TransactionImportRepository transactionImportRepository,
                               TransactionRepository transactionRepository, TransferRepository transferRepository,
                               AuditLogService auditLogService, PlatformTransactionManager transactionManager,
                               Clock clock) {
        this.transactionImportRepository = transactionImportRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.auditLogService = auditLogService;
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
     * transfers, and saves the rest with the counters, all or nothing.
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
        DuplicateDetector.Result checked = transactionTemplate.execute(status -> {
            // Story 5.5: imports into the same target run one at a time from here to the commit, so each sees the rows
            // the other saved
            UUID targetId = creditCard ? transactionImport.getCreditCardId() : transactionImport.getAccountId();
            transactionImportRepository.lockTarget(lockKey(targetId));
            DuplicateDetector.Result detection = detect(transactionImport, fileType, result.entries());
            int total = result.totalRows();
            OffsetDateTime now = OffsetDateTime.now(clock);
            // the guarded update first: if the import already left PROCESSING nothing is written
            int updated = detection.toSave().isEmpty()
                ? transactionImportRepository.markCompletedWithNothingNew(importId, total, detection.duplicates(),
                    detection.transfers(), skipped, now)
                : transactionImportRepository.markReadyForReview(importId, total, detection.toSave().size(),
                    detection.duplicates(), detection.transfers(), skipped, now);
            if (updated == 0) {
                return null;
            }
            if (!detection.toSave().isEmpty()) {
                transactionRepository.saveAll(detection.toSave().stream()
                    .map(entry -> toTransaction(transactionImport, entry))
                    .toList());
            }
            auditLogService.log(AUDIT_DUPLICATES_CHECKED, transactionImport.getUserId(), null,
                auditMetadata(importId, total, skipped, detection));
            return detection;
        });
        if (checked == null) {
            log.info("Import {} parsed but no longer processing: nothing saved", importId);
        } else if (checked.toSave().isEmpty()) {
            log.info("Import {} completed with nothing new: {} duplicate(s), {} transfer(s), {} skipped row(s)",
                importId, checked.duplicates(), checked.transfers(), skipped);
        } else {
            log.info("Import {} ready for review: {} transaction(s), {} duplicate(s), {} transfer(s), {} skipped row(s)",
                importId, checked.toSave().size(), checked.duplicates(), checked.transfers(), skipped);
        }
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
            existing = transactionRepository.findActiveByAccountBetween(userId, accountId, from, to);
            transfers = transferRepository.findActiveByAccountBetween(userId, accountId, from, to);
        } else {
            existing = transactionRepository.findActiveByCreditCardBetween(userId,
                transactionImport.getCreditCardId(), from, to);
            transfers = List.of();
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

    /** Advisory lock key of a target (account or card id). A collision only serializes two unrelated imports. */
    static long lockKey(UUID targetId) {
        return targetId.getMostSignificantBits() ^ targetId.getLeastSignificantBits();
    }

    /** Only the import id and the counts: never descriptions nor file content. */
    static String auditMetadata(UUID importId, int total, int skipped, DuplicateDetector.Result detection) {
        return "{\"import_id\":\"" + importId + "\",\"total_transactions\":" + total
            + ",\"imported_transactions\":" + detection.toSave().size()
            + ",\"duplicates_found\":" + detection.duplicates()
            + ",\"transfers_found\":" + detection.transfers()
            + ",\"skipped_rows\":" + skipped + "}";
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
        transaction.setReviewStatus(Transaction.ReviewStatus.PENDING);
        transaction.setAffectsBalance(false);
        // Story 5.6: after the duplicates, so the detector never changes how many rows are saved
        Optional<String> pixDetails = PixDetector.detect(entry.description(), entry.type());
        transaction.setPix(pixDetails.isPresent());
        transaction.setPixDetails(pixDetails.orElse(null));
        transaction.setInstallment(false);
        transaction.setFutureInstallment(false);
        return transaction;
    }

    /** At startup: every import still PROCESSING that was created before this backend came up has no processing. */
    @EventListener(ApplicationReadyEvent.class)
    public void failImportsInterruptedByRestart() {
        int failed = transactionImportRepository.failProcessingCreatedBefore(startedAt, INTERRUPTED_MESSAGE,
            OffsetDateTime.now(clock));
        if (failed > 0) {
            log.info("{} import(s) interrupted by restart marked as failed", failed);
        }
    }

    /** Every 5 minutes: imports PROCESSING for more than 15 minutes (processing lost or stuck). */
    @Scheduled(fixedRate = 300_000, initialDelay = 300_000)
    public void failStaleImports() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int failed = transactionImportRepository.failProcessingCreatedBefore(now.minus(ORPHAN_AGE),
            INTERRUPTED_MESSAGE, now);
        if (failed > 0) {
            log.info("{} stale import(s) marked as failed", failed);
        }
    }
}
