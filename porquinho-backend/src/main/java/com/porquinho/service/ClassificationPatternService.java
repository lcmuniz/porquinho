package com.porquinho.service;

import com.porquinho.entity.UserClassificationPattern;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.UserClassificationPatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Story 5.11: learns from the categories the user saves and turns them into patterns by normalized description, which
 * the next imports apply without the AI. Exact match only. Logs carry only ids and counts, never a pattern.
 */
@Service
public class ClassificationPatternService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationPatternService.class);

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}]+");
    /** Decision Q4: generic banking words, already without accents. A pattern made only of them is like an empty one. */
    private static final Set<String> GENERIC_TERMS = Set.of("pix", "ted", "doc", "transf", "transferencia", "pagamento",
        "pagto", "boleto", "compra", "debito", "credito", "enviado", "enviada", "recebido", "recebida", "tarifa", "saque",
        "deposito");

    private final UserClassificationPatternRepository patternRepository;
    private final AiClassificationHistoryRepository historyRepository;
    private final TransactionRepository transactionRepository;

    public ClassificationPatternService(UserClassificationPatternRepository patternRepository,
                                        AiClassificationHistoryRepository historyRepository,
                                        TransactionRepository transactionRepository) {
        this.patternRepository = patternRepository;
        this.historyRepository = historyRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * The pattern of {@code description}: without the installment marker, lower case, without accents, and only its
     * letters, in words separated by one space (dates, numbers and documents go away). Empty learns and matches nothing.
     */
    public static String normalize(String description) {
        if (description == null) {
            return "";
        }
        String base = InstallmentDetector.detect(description).map(InstallmentDetector.Installment::baseDescription)
            .orElse(description);
        String plain = MARKS.matcher(Normalizer.normalize(base.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
            .replaceAll("");
        return NOT_LETTERS.matcher(plain).replaceAll(" ").trim();
    }

    /** Whether {@code pattern} may learn and match: not empty and not only generic banking words (decision Q4). */
    public static boolean isUsable(String pattern) {
        for (String word : pattern.split(" ")) {
            if (!word.isEmpty() && !GENERIC_TERMS.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The user saved {@code categoryId} on transactions {@code ids} (decision Q2): their AI history records whether it
     * was a correction, and each pattern of their descriptions now points to it. Runs in the caller's transaction.
     */
    @Transactional
    public void learn(UUID userId, Collection<UUID> ids, UUID categoryId) {
        int history = historyRepository.markUserChoice(userId, ids, categoryId);
        // sorted, so that two saves at the same time lock the pattern rows in the same order
        Set<String> patterns = new TreeSet<>();
        for (String description : transactionRepository.findDescriptions(userId, ids)) {
            String pattern = normalize(description);
            if (isUsable(pattern)) {
                patterns.add(pattern);
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (String pattern : patterns) {
            patternRepository.upsert(userId, pattern, categoryId, now);
        }
        log.info("User {} saved category {} on {} transaction(s): {} history row(s), {} pattern(s)", userId, categoryId,
            ids.size(), history, patterns.size());
    }

    /** The user's usable patterns (active child category), pattern → category. */
    public Map<String, UUID> match(UUID userId) {
        Map<String, UUID> patterns = new HashMap<>();
        for (UserClassificationPattern pattern : patternRepository.findUsableByUserId(userId)) {
            patterns.put(pattern.getDescriptionPattern(), pattern.getPreferredCategoryId());
        }
        return patterns;
    }

    /** Marks {@code patterns} of the user as applied now. Runs in the caller's transaction. */
    @Transactional
    public void touch(UUID userId, Collection<String> patterns, OffsetDateTime now) {
        patternRepository.touch(userId, patterns, now);
    }
}
