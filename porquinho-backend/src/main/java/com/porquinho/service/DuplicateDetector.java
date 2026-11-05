package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.entity.Transfer;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Story 5.5: drops the entries of an import that are already in the target. Pure, no Spring. Each existing
 * transaction and each transfer absorbs at most one entry; entries of the same file are never compared with each other.
 * In order:
 * <ol>
 *   <li>same non-null {@code external_id} → duplicate;</li>
 *   <li>among the rest: same type and amount, date within ±1 day and similar original description → duplicate. Only
 *       when one of the external ids is missing, or when they differ and the existing transaction came from an import
 *       of the other format (decision 4A: banks use different ids in OFX and CSV, but within one format a different id
 *       is a different transaction);</li>
 *   <li>account only (decision D4): an active transfer of the same amount, date within ±1 day, from the account with a
 *       DEBIT entry or to the account with a CREDIT entry → already registered transfer.</li>
 * </ol>
 * The caller passes only the target's active transactions and transfers (the scope and the date window are queries).
 */
public final class DuplicateDetector {

    /** Minimum Levenshtein similarity ({@code 1 - distance / max length}) of two normalized descriptions. */
    static final double MIN_SIMILARITY = 0.90;
    /** A shorter normalized description at least this long that prefixes the longer one also counts (BB OFX MEMO cut). */
    static final int MIN_PREFIX_LENGTH = 10;
    /** Maximum distance in days between an entry and the transaction or transfer it matches. */
    static final int MAX_DAYS_APART = 1;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Entries to save (file order kept) and how many were dropped as duplicates or as registered transfers. */
    public record Result(List<OfxTransactionParser.Entry> toSave, int duplicates, int transfers) {
    }

    private DuplicateDetector() {
    }

    /**
     * @param entries         the entries read from the file
     * @param fileType        the format of the file being imported
     * @param existing        the target's active transactions (any review status) around the file dates
     * @param importFileTypes the format of the imports the existing transactions came from, by import id (a
     *                        transaction without import, or whose import is missing here, counts as no format)
     * @param transfers the user's active transfers from or to the account around the file dates (ignored for a card)
     * @param accountId the target account, or null when the target is a credit card (no transfer check)
     */
    public static Result detect(List<OfxTransactionParser.Entry> entries, TransactionImport.FileType fileType,
                                Collection<Transaction> existing,
                                Map<UUID, TransactionImport.FileType> importFileTypes,
                                Collection<Transfer> transfers, UUID accountId) {
        int count = entries.size();
        boolean[] dropped = new boolean[count];
        int duplicates = 0;

        // 1. exact external id
        Map<String, Deque<Candidate>> byExternalId = new HashMap<>();
        Map<String, List<Candidate>> byTypeAndAmount = new HashMap<>();
        for (Transaction transaction : existing) {
            TransactionImport.FileType origin = transaction.getImportId() == null
                ? null : importFileTypes.get(transaction.getImportId());
            Candidate candidate = new Candidate(transaction, origin != null && origin != fileType);
            if (transaction.getExternalId() != null) {
                byExternalId.computeIfAbsent(transaction.getExternalId(), k -> new ArrayDeque<>()).add(candidate);
            }
            byTypeAndAmount.computeIfAbsent(key(transaction.getTransactionType(), transaction.getAmount()),
                k -> new ArrayList<>()).add(candidate);
        }
        for (int i = 0; i < count; i++) {
            String externalId = entries.get(i).externalId();
            if (externalId == null) {
                continue;
            }
            Deque<Candidate> sameId = byExternalId.get(externalId);
            Candidate match = sameId == null ? null : pollUnused(sameId);
            if (match != null) {
                match.used = true;
                dropped[i] = true;
                duplicates++;
            }
        }

        // 2. similarity, only with what is left of step 1
        for (int i = 0; i < count; i++) {
            if (dropped[i]) {
                continue;
            }
            OfxTransactionParser.Entry entry = entries.get(i);
            List<Candidate> candidates = byTypeAndAmount.get(key(entry.type(), entry.amount()));
            if (candidates == null) {
                continue;
            }
            String description = null;
            Candidate best = null;
            long bestDays = Long.MAX_VALUE;
            for (Candidate candidate : candidates) {
                long days = daysApart(entry.date(), candidate.transaction.getTransactionDate());
                if (candidate.used || days > MAX_DAYS_APART || days >= bestDays
                    || !idsAllowSimilarity(entry.externalId(), candidate)) {
                    continue;
                }
                if (description == null) {
                    description = normalize(entry.originalDescription());
                }
                if (similarNormalized(description, candidate.description())) {
                    best = candidate;
                    bestDays = days;
                }
            }
            if (best != null) {
                best.used = true;
                dropped[i] = true;
                duplicates++;
            }
        }

        // 3. transfers already registered (account only, decision D4)
        int transferCount = 0;
        if (accountId != null && !transfers.isEmpty()) {
            List<Transfer> available = new ArrayList<>(transfers);
            for (int i = 0; i < count; i++) {
                if (dropped[i]) {
                    continue;
                }
                OfxTransactionParser.Entry entry = entries.get(i);
                int bestIndex = -1;
                long bestDays = Long.MAX_VALUE;
                for (int t = 0; t < available.size(); t++) {
                    Transfer transfer = available.get(t);
                    if (!rightDirection(transfer, accountId, entry.type())
                        || transfer.getAmount().compareTo(entry.amount()) != 0) {
                        continue;
                    }
                    long days = daysApart(entry.date(), transfer.getTransferDate());
                    if (days <= MAX_DAYS_APART && days < bestDays) {
                        bestIndex = t;
                        bestDays = days;
                    }
                }
                if (bestIndex >= 0) {
                    available.remove(bestIndex);
                    dropped[i] = true;
                    transferCount++;
                }
            }
        }

        List<OfxTransactionParser.Entry> toSave = new ArrayList<>(count - duplicates - transferCount);
        for (int i = 0; i < count; i++) {
            if (!dropped[i]) {
                toSave.add(entries.get(i));
            }
        }
        return new Result(toSave, duplicates, transferCount);
    }

    /**
     * Lower case, no accents (NFD), every run of characters that are not letters or digits becomes one space, trimmed.
     * Null becomes "".
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return NOT_LETTER_OR_DIGIT.matcher(noAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** Whether two raw descriptions are similar (after {@link #normalize}). */
    static boolean similar(String a, String b) {
        return similarNormalized(normalize(a), normalize(b));
    }

    private static boolean similarNormalized(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        String shorter = a.length() <= b.length() ? a : b;
        String longer = shorter == a ? b : a;
        if (shorter.length() >= MIN_PREFIX_LENGTH && longer.startsWith(shorter)) {
            return true;
        }
        int maxLength = longer.length();
        // the distance is at least the length difference: skip the quadratic part when that alone fails
        if (maxLength == 0 || 1.0 - (double) (maxLength - shorter.length()) / maxLength < MIN_SIMILARITY) {
            return false;
        }
        return 1.0 - (double) levenshtein(a, b) / maxLength >= MIN_SIMILARITY;
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /**
     * Step 2 applies when one of the external ids is missing, or when both are present, differ and the existing
     * transaction came from the other format (decision 4A). Equal ids were for step 1 only.
     */
    private static boolean idsAllowSimilarity(String entryExternalId, Candidate candidate) {
        String existingExternalId = candidate.transaction.getExternalId();
        if (entryExternalId == null || existingExternalId == null) {
            return true;
        }
        return !entryExternalId.equals(existingExternalId) && candidate.otherFormat;
    }

    private static boolean rightDirection(Transfer transfer, UUID accountId, Transaction.Type type) {
        return (type == Transaction.Type.DEBIT && accountId.equals(transfer.getFromAccountId()))
            || (type == Transaction.Type.CREDIT && accountId.equals(transfer.getToAccountId()));
    }

    private static long daysApart(LocalDate a, LocalDate b) {
        return Math.abs(ChronoUnit.DAYS.between(a, b));
    }

    private static String key(Transaction.Type type, BigDecimal amount) {
        return type + ":" + amount.stripTrailingZeros().toPlainString();
    }

    private static Candidate pollUnused(Deque<Candidate> candidates) {
        while (!candidates.isEmpty()) {
            Candidate candidate = candidates.poll();
            if (!candidate.used) {
                return candidate;
            }
        }
        return null;
    }

    /** An existing transaction and whether it already absorbed an entry of this import. */
    private static final class Candidate {
        private final Transaction transaction;
        /** Whether the transaction came from an import of the other format (OFX × CSV). */
        private final boolean otherFormat;
        private String normalizedDescription;
        private boolean used;

        private Candidate(Transaction transaction, boolean otherFormat) {
            this.transaction = transaction;
            this.otherFormat = otherFormat;
        }

        private String description() {
            if (normalizedDescription == null) {
                normalizedDescription = normalize(transaction.getOriginalDescription());
            }
            return normalizedDescription;
        }
    }
}
