package com.porquinho.service;

import com.porquinho.repository.TransactionImportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Background processing of an uploaded statement (Story 5.3). Runs after the import row is committed, outside the
 * request thread, with the file content only in memory: detects the bank of the OFX (the import stays PROCESSING with
 * bank_name set, decision 1A) or marks the import FAILED with a clear message. Story 5.4 extends this same processing
 * with the parsing. Imports left PROCESSING with no processing running (backend restarted, upload interrupted) are
 * failed at startup and every 5 minutes. Logs carry only the import id and the reason, never file content.
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

    /** A PROCESSING import older than this has no processing running anymore. */
    static final Duration ORPHAN_AGE = Duration.ofMinutes(15);

    private final TransactionImportRepository transactionImportRepository;
    private final Clock clock;
    /** Taken when the bean is created, before the web server accepts uploads: older PROCESSING rows are orphans. */
    private final OffsetDateTime startedAt;

    @Autowired
    public TransactionImportProcessor(TransactionImportRepository transactionImportRepository) {
        this(transactionImportRepository, Clock.systemDefaultZone());
    }

    TransactionImportProcessor(TransactionImportRepository transactionImportRepository, Clock clock) {
        this.transactionImportRepository = transactionImportRepository;
        this.clock = clock;
        this.startedAt = OffsetDateTime.now(clock);
    }

    /**
     * Processes the committed import {@code importId}. Never throws: any failure becomes FAILED with a generic
     * message. The content is dropped when this returns.
     */
    @Async
    public void process(UUID importId, byte[] content) {
        try {
            if (OfxBankDetector.isTruncated(content)) {
                log.info("Import {} failed: truncated OFX", importId);
                transactionImportRepository.markFailed(importId, TRUNCATED_MESSAGE, OffsetDateTime.now(clock));
                return;
            }
            String bank = OfxBankDetector.detectBank(content);
            int updated = transactionImportRepository.recordBank(importId, bank, OffsetDateTime.now(clock));
            log.info("Import {} bank detected: {}{}", importId, bank, updated == 0 ? " (no longer processing)" : "");
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
