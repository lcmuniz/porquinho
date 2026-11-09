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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Classifies the rows of an import already committed (Story 5.8), outside the advisory lock of the import, while it is
 * still PROCESSING. The rows go in sequential batches of up to {@value #BATCH_SIZE}, each one sent to the AI in
 * simultaneous calls of up to {@value #CALL_SIZE} (NFR7: the time of a call grows with its answer), and one short
 * database transaction per batch, whatever its result: the classification with its history, the future installments
 * of the batch (decision 1A) and the progress, which also keeps {@code updated_at} fresh for the orphan sweep.
 *
 * <p>The rows are born "Não classificado" ({@code NONE}, confidence 0, see {@link TransactionImportProcessor}), so a
 * failed batch or a discarded item writes nothing to them. Any confidence keeps the category: flagged for review is
 * {@code classification_confidence < 80}. Logs carry only ids and counts.
 *
 * <p>Story 5.11: before the AI, the rows whose normalized description has a usable pattern of the user get its
 * category ({@code PATTERN}, confidence {@value #PATTERN_CONFIDENCE}), and so do their future installments. They never
 * go to the AI, do not count in {@code classification_total} and leave no history.
 */
@Service
public class TransactionClassificationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionClassificationService.class);

    static final int BATCH_SIZE = 100;
    /** Rows per AI call: 100 in one call took 5.5–6.7 s against the real API, and 25 took 2.2–2.6 s (NFR7: < 3 s). */
    static final int CALL_SIZE = 25;
    /** Decision Q1: a row classified by a pattern of the user is high confidence. */
    static final int PATTERN_CONFIDENCE = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionImportRepository transactionImportRepository;
    private final CategoryRepository categoryRepository;
    private final AiClassificationHistoryRepository historyRepository;
    private final TransactionClassifier classifier;
    private final ClassificationPatternService patternService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public TransactionClassificationService(TransactionRepository transactionRepository,
                                            TransactionImportRepository transactionImportRepository,
                                            CategoryRepository categoryRepository,
                                            AiClassificationHistoryRepository historyRepository,
                                            TransactionClassifier classifier,
                                            ClassificationPatternService patternService,
                                            PlatformTransactionManager transactionManager) {
        this(transactionRepository, transactionImportRepository, categoryRepository, historyRepository, classifier,
            patternService, transactionManager, Clock.systemDefaultZone());
    }

    TransactionClassificationService(TransactionRepository transactionRepository,
                                     TransactionImportRepository transactionImportRepository,
                                     CategoryRepository categoryRepository,
                                     AiClassificationHistoryRepository historyRepository,
                                     TransactionClassifier classifier, ClassificationPatternService patternService,
                                     PlatformTransactionManager transactionManager, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.transactionImportRepository = transactionImportRepository;
        this.categoryRepository = categoryRepository;
        this.historyRepository = historyRepository;
        this.classifier = classifier;
        this.patternService = patternService;
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
        List<Transaction> classifiable = transactionRepository.findClassifiableByImport(userId, importId);
        Map<Integer, UUID> categoryIds = new HashMap<>();
        List<TransactionClassifier.CategoryChoice> categories = categoryChoices(userId, categoryIds);
        if (classifiable.isEmpty() || categories.isEmpty()) {
            log.info("Import {} has nothing to classify: {} row(s), {} categories", importId, classifiable.size(),
                categories.size());
            return;
        }
        List<Transaction> rows = applyPatterns(importId, userId, classifiable);
        if (rows == null) {
            log.info("Import {} no longer processing: not classified", importId);
            return;
        }
        if (rows.isEmpty()) {
            log.info("Import {}: every row classified by the user's patterns, AI not called", importId);
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

    /**
     * Story 5.11: classifies the rows that match a usable pattern of the user, with their future installments, in one
     * transaction guarded by PROCESSING. Returns the rows left for the AI, or null when the import left PROCESSING. A
     * failure here sends every row to the AI, as before the patterns.
     */
    private List<Transaction> applyPatterns(UUID importId, UUID userId, List<Transaction> rows) {
        Map<Transaction, UUID> matched = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        List<Transaction> rest = new ArrayList<>();
        Boolean applied;
        try {
            Map<String, UUID> patterns = patternService.match(userId);
            for (Transaction row : rows) {
                String pattern = ClassificationPatternService.normalize(row.getDescription());
                UUID categoryId = ClassificationPatternService.isUsable(pattern) ? patterns.get(pattern) : null;
                if (categoryId == null) {
                    rest.add(row);
                } else {
                    matched.put(row, categoryId);
                    used.add(pattern);
                }
            }
            if (matched.isEmpty()) {
                return rows;
            }
            applied = transactionTemplate.execute(status -> {
                OffsetDateTime now = OffsetDateTime.now(clock);
                if (transactionImportRepository.addClassified(importId, 0, now) == 0) {
                    return false;
                }
                for (Map.Entry<Transaction, UUID> entry : matched.entrySet()) {
                    Transaction row = entry.getKey();
                    transactionRepository.applyClassification(row.getId(), entry.getValue(), PATTERN_CONFIDENCE,
                        Transaction.ClassificationSource.PATTERN, now);
                    if (row.getInstallmentGroupId() != null) {
                        transactionRepository.applyClassificationToFutures(importId, row.getInstallmentGroupId(),
                            entry.getValue(), PATTERN_CONFIDENCE, Transaction.ClassificationSource.PATTERN, now);
                    }
                }
                patternService.touch(userId, used, now);
                return true;
            });
        } catch (Exception e) {
            log.warn("Import {} patterns not applied: {}", importId, e.getClass().getName());
            return rows;
        }
        if (!Boolean.TRUE.equals(applied)) {
            return null;
        }
        log.info("Import {}: {} row(s) classified by {} pattern(s) of the user", importId, matched.size(), used.size());
        return rest;
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
        List<Future<List<TransactionClassifier.Result>>> answers = new ArrayList<>();
        try (ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int from = 0; from < items.size(); from += CALL_SIZE) {
                List<TransactionClassifier.Item> part = items.subList(from, Math.min(from + CALL_SIZE, items.size()));
                answers.add(calls.submit(() -> askPart(importId, part, categories)));
            }
        }
        return answers.stream().flatMap(answer -> answer.resultNow().stream()).toList();
    }

    /** One AI call. A failed one leaves only its rows unclassified; an index of another call is an unknown index. */
    private List<TransactionClassifier.Result> askPart(UUID importId, List<TransactionClassifier.Item> part,
                                                       List<TransactionClassifier.CategoryChoice> categories) {
        int first = part.getFirst().index();
        int last = part.getLast().index();
        try {
            return classifier.classify(part, categories).stream()
                .filter(result -> result.index() >= first && result.index() <= last)
                .toList();
        } catch (Exception e) {
            // a failed call never stops the others
            log.warn("Import {} AI call failed: {}", importId, e.getClass().getName());
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
                transactionRepository.applyClassification(row.getId(), categoryId, result.confidence(),
                    Transaction.ClassificationSource.AI, now);
                if (row.getInstallmentGroupId() != null) {
                    transactionRepository.applyClassificationToFutures(importId, row.getInstallmentGroupId(),
                        categoryId, result.confidence(), Transaction.ClassificationSource.AI, now);
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
