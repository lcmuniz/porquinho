package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Background processing of an uploaded statement (Stories 5.3 and 5.4). Runs after the import row is committed,
 * outside the request thread, with the file content only in memory: detects the bank of the OFX (or takes it from the
 * CSV layout, Story 5.2), reads its entries ({@link OfxTransactionParser}, {@link CsvStatementReader}) and saves the valid ones as PENDING together with the import counters, moving the
 * import to REVIEW (decision 1A), all in one database transaction. Otherwise the import becomes FAILED with a clear
 * message and nothing is saved. Imports left PROCESSING with no processing running (backend restarted, upload
 * interrupted) are failed at startup and every 5 minutes. Logs carry only the import id, positions and reasons,
 * never file content.
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

    /** A PROCESSING import older than this has no processing running anymore. */
    static final Duration ORPHAN_AGE = Duration.ofMinutes(15);

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    /** Taken when the bean is created, before the web server accepts uploads: older PROCESSING rows are orphans. */
    private final OffsetDateTime startedAt;

    @Autowired
    public TransactionImportProcessor(TransactionImportRepository transactionImportRepository,
                                      TransactionRepository transactionRepository,
                                      PlatformTransactionManager transactionManager) {
        this(transactionImportRepository, transactionRepository, transactionManager, Clock.systemDefaultZone());
    }

    TransactionImportProcessor(TransactionImportRepository transactionImportRepository,
                               TransactionRepository transactionRepository,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.transactionImportRepository = transactionImportRepository;
        this.transactionRepository = transactionRepository;
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

    /** Stories 5.4 and 5.2: reads the entries (OFX or CSV) and saves them with the counters, all or nothing. */
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
        List<Transaction> transactions = result.entries().stream()
            .map(entry -> toTransaction(transactionImport, entry))
            .toList();
        Boolean saved = transactionTemplate.execute(status -> {
            // the guarded update first: if the import already left PROCESSING nothing is written
            int updated = transactionImportRepository.markReadyForReview(importId, result.totalRows(),
                transactions.size(), result.skippedRows().size(), OffsetDateTime.now(clock));
            if (updated == 0) {
                return false;
            }
            transactionRepository.saveAll(transactions);
            return true;
        });
        if (Boolean.TRUE.equals(saved)) {
            log.info("Import {} ready for review: {} transaction(s), {} skipped row(s)", importId,
                transactions.size(), result.skippedRows().size());
        } else {
            log.info("Import {} parsed but no longer processing: nothing saved", importId);
        }
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
        transaction.setPix(false);
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
