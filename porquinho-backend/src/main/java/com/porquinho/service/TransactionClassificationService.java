package com.porquinho.service;

import com.porquinho.entity.AiClassificationHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Classifies the rows of an import already committed (Story 5.8), outside the advisory lock of the import, while it is
 * still PROCESSING. The rows go in sequential batches of up to {@value #BATCH_SIZE}, one short database transaction
 * per batch, whatever its result: the classification with its history, the future installments of the batch (decision
 * 1A) and the progress, which also keeps {@code updated_at} fresh for the orphan sweep.
 *
 * <p>The rows are born "Não classificado" ({@code NONE}, confidence 0, see {@link TransactionImportProcessor}), so a
 * failed batch or a discarded item writes nothing to them. Any confidence keeps the category: flagged for review is
 * {@code classification_confidence < 80}. Logs carry only ids and counts.
 */
@Service
public class TransactionClassificationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionClassificationService.class);

    static final int BATCH_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionImportRepository transactionImportRepository;
    private final CategoryRepository categoryRepository;
    private final AiClassificationHistoryRepository historyRepository;
    private final TransactionClassifier classifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public TransactionClassificationService(TransactionRepository transactionRepository,
                                            TransactionImportRepository transactionImportRepository,
                                            CategoryRepository categoryRepository,
                                            AiClassificationHistoryRepository historyRepository,
                                            TransactionClassifier classifier,
                                            PlatformTransactionManager transactionManager) {
        this(transactionRepository, transactionImportRepository, categoryRepository, historyRepository, classifier,
            transactionManager, Clock.systemDefaultZone());
    }

    TransactionClassificationService(TransactionRepository transactionRepository,
                                     TransactionImportRepository transactionImportRepository,
                                     CategoryRepository categoryRepository,
                                     AiClassificationHistoryRepository historyRepository,
                                     TransactionClassifier classifier, PlatformTransactionManager transactionManager,
                                     Clock clock) {
        this.transactionRepository = transactionRepository;
        this.transactionImportRepository = transactionImportRepository;
        this.categoryRepository = categoryRepository;
        this.historyRepository = historyRepository;
        this.classifier = classifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Classifies import {@code importId} of {@code userId}. Never throws: whatever failed stays "Não classificado". */
    public void classify(UUID importId, UUID userId, boolean creditCard) {
        try {
            classifyBatches(importId, userId, creditCard);
        } catch (Exception e) {
            log.error("Import {} classification stopped: {}", importId, e.getClass().getName());
        }
    }

    private void classifyBatches(UUID importId, UUID userId, boolean creditCard) {
        List<Transaction> rows = transactionRepository.findClassifiableByImport(userId, importId);
        Map<Integer, UUID> categoryIds = new HashMap<>();
        List<TransactionClassifier.CategoryChoice> categories = categoryChoices(userId, categoryIds);
        if (rows.isEmpty() || categories.isEmpty()) {
            log.info("Import {} has nothing to classify: {} row(s), {} categories", importId, rows.size(),
                categories.size());
            return;
        }
        if (transactionImportRepository.startClassification(importId, rows.size(), OffsetDateTime.now(clock)) == 0) {
            log.info("Import {} no longer processing: not classified", importId);
            return;
        }
        int failed = 0;
        int answered = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Transaction> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            List<TransactionClassifier.Result> results = ask(importId, batch, categories, creditCard);
            Map<Integer, TransactionClassifier.Result> accepted = accepted(results, batch.size(), categoryIds);
            Boolean applied;
            try {
                applied = transactionTemplate.execute(status -> apply(importId, userId, batch, accepted, categoryIds));
            } catch (Exception e) {
                // a batch that could not be saved never stops the next ones; its rows stay "Não classificado"
                log.warn("Import {} batch not saved: {}", importId, e.getClass().getName());
                failed++;
                continue;
            }
            if (!Boolean.TRUE.equals(applied)) {
                log.info("Import {} no longer processing: classification stopped", importId);
                return;
            }
            if (results.isEmpty()) {
                failed++;
            }
            answered += accepted.size();
        }
        log.info("Import {} classified: {} row(s) sent, {} answered, {} failed batch(es)", importId, rows.size(),
            answered, failed);
    }

    /** The active child categories, numbered from 1: groups are not transaction categories. */
    private List<TransactionClassifier.CategoryChoice> categoryChoices(UUID userId, Map<Integer, UUID> categoryIds) {
        List<Category> active = categoryRepository.findActiveByUserId(userId);
        Map<UUID, String> names = new HashMap<>();
        for (Category category : active) {
            names.put(category.getId(), category.getCategoryName());
        }
        List<TransactionClassifier.CategoryChoice> choices = new ArrayList<>();
        for (Category category : active) {
            if (category.getParentCategoryId() != null) {
                int id = choices.size() + 1;
                categoryIds.put(id, category.getId());
                choices.add(new TransactionClassifier.CategoryChoice(id, category.getCategoryName(),
                    names.get(category.getParentCategoryId())));
            }
        }
        return choices;
    }

    private List<TransactionClassifier.Result> ask(UUID importId, List<Transaction> batch,
                                                   List<TransactionClassifier.CategoryChoice> categories,
                                                   boolean creditCard) {
        List<TransactionClassifier.Item> items = new ArrayList<>(batch.size());
        for (int index = 0; index < batch.size(); index++) {
            Transaction row = batch.get(index);
            items.add(new TransactionClassifier.Item(index, SensitiveDataMasker.mask(row.getDescription()),
                row.getAmount(), row.getTransactionType().name(), creditCard ? "cartão" : "conta"));
        }
        try {
            return classifier.classify(items, categories);
        } catch (Exception e) {
            // a failed batch never stops the next ones
            log.warn("Import {} batch failed: {}", importId, e.getClass().getName());
            return List.of();
        }
    }

    /**
     * The items worth applying, by index. An unknown or repeated index, a confidence outside 0–100 or a category that
     * was not sent discards the item; a repeated index discards all of its items, since none can be told right.
     */
    static Map<Integer, TransactionClassifier.Result> accepted(List<TransactionClassifier.Result> results, int batchSize,
                                                               Map<Integer, UUID> categoryIds) {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> repeated = new HashSet<>();
        for (TransactionClassifier.Result result : results) {
            if (!seen.add(result.index())) {
                repeated.add(result.index());
            }
        }
        Map<Integer, TransactionClassifier.Result> accepted = new HashMap<>();
        for (TransactionClassifier.Result result : results) {
            boolean knownIndex = result.index() >= 0 && result.index() < batchSize;
            boolean validConfidence = result.confidence() >= 0 && result.confidence() <= 100;
            boolean knownCategory = result.categoryId() == null || categoryIds.containsKey(result.categoryId());
            if (knownIndex && validConfidence && knownCategory && !repeated.contains(result.index())) {
                accepted.put(result.index(), result);
            }
        }
        return accepted;
    }

    /**
     * One batch, all or nothing. The guarded progress update comes first: it holds the import row, so an import that
     * left PROCESSING (failed as orphan, account purged) writes nothing and stops the loop. {@code false} in that case.
     * The rows are ordered by date, so with two installments of a purchase in the file the futures end up with the
     * classification of the latest one that was answered.
     */
    private boolean apply(UUID importId, UUID userId, List<Transaction> batch,
                          Map<Integer, TransactionClassifier.Result> accepted, Map<Integer, UUID> categoryIds) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (transactionImportRepository.addClassified(importId, batch.size(), now) == 0) {
            return false;
        }
        List<AiClassificationHistory> history = new ArrayList<>(accepted.size());
        for (Map.Entry<Integer, TransactionClassifier.Result> entry : accepted.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).toList()) {
            Transaction row = batch.get(entry.getKey());
            TransactionClassifier.Result result = entry.getValue();
            UUID categoryId = result.categoryId() == null ? null : categoryIds.get(result.categoryId());
            if (categoryId != null) {
                transactionRepository.applyAiClassification(row.getId(), categoryId, result.confidence(), now);
                if (row.getInstallmentGroupId() != null) {
                    transactionRepository.applyAiClassificationToFutures(importId, row.getInstallmentGroupId(),
                        categoryId, result.confidence(), now);
                }
            }
            history.add(new AiClassificationHistory(userId, row.getId(), categoryId, result.confidence()));
        }
        if (!history.isEmpty()) {
            historyRepository.saveAll(history);
        }
        return true;
    }
}
