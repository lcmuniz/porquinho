package com.porquinho.service;

import com.porquinho.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Story 5.7: turns the rows of a card import into installments, groups and future installments. Pure, no Spring.
 * For each card purchase ({@code DEBIT}, {@code REGULAR}) with an installment marker in its original description
 * ({@link InstallmentDetector}, the one source of the number, the total and the base):
 * <ol>
 *   <li>it joins the first group (oldest first, then the groups opened by this import) with the same total, an
 *       amount at most {@link #amountTolerance} apart (rounding cents, decision 3A), a similar base description
 *       ({@link DuplicateDetector#similar}, from the original description, which the user never edits), its number
 *       free or still future and its date at most {@link #MAX_DATE_DISTANCE_DAYS} days from the date expected for that
 *       number (decision 4A); otherwise it opens a new group;</li>
 *   <li>its number already future in the group: a future saved before is confirmed with the data of the file
 *       (decision D7: nothing inserted, counted as duplicate by the caller, decision 2A); a future created by this same
 *       import is replaced by the row;</li>
 *   <li>otherwise the row is inserted and the futures of every number after it up to the total that the group does
 *       not have yet are created (so an older month imported later creates none).</li>
 * </ol>
 */
final class InstallmentPlanner {

    /** Column size of {@code description} and {@code original_description}. */
    static final int MAX_DESCRIPTION = 500;
    /** Decision 3A: the least tolerance, whatever the total (333,34 + 2 × 333,33). */
    static final BigDecimal MIN_AMOUNT_TOLERANCE = new BigDecimal("0.05");
    /** Decision 3A: what each installment after the first can leave to the one with the rounding cents. */
    static final BigDecimal AMOUNT_TOLERANCE_PER_INSTALLMENT = new BigDecimal("0.01");
    /** Decision 4A: keeps a repeated purchase (next year's annual fee) out of the previous one's group. */
    static final int MAX_DATE_DISTANCE_DAYS = 20;

    /**
     * @param toInsert       the rows to save, in file order, each followed by the futures it created
     * @param confirmed      the futures saved before and now confirmed (managed entities, already updated)
     * @param installments   how many rows of the file are installments (confirmed ones included)
     * @param futuresCreated how many future installments are in {@code toInsert}
     */
    record Plan(List<Transaction> toInsert, List<Transaction> confirmed, int installments, int futuresCreated) {
    }

    private InstallmentPlanner() {
    }

    /**
     * @param rows     the rows built from the file entries left after the duplicate check, not saved yet
     * @param existing the card's active installments, oldest first ({@code created_at}, {@code id})
     */
    static Plan plan(List<Transaction> rows, List<Transaction> existing) {
        Set<Transaction> saved = Collections.newSetFromMap(new IdentityHashMap<>());
        saved.addAll(existing);
        Map<UUID, Group> groups = new LinkedHashMap<>();
        for (Transaction transaction : existing) {
            if (transaction.getInstallmentGroupId() == null || transaction.getInstallmentNumber() == null
                || transaction.getInstallmentTotal() == null) {
                continue;
            }
            Group group = groups.computeIfAbsent(transaction.getInstallmentGroupId(), id -> new Group(id,
                transaction.getInstallmentTotal(), transaction.getAmount(),
                baseOf(transaction.getOriginalDescription())));
            // an already confirmed row wins over a future of the same number
            group.members.merge(transaction.getInstallmentNumber(), transaction,
                (kept, other) -> kept.isFutureInstallment() ? other : kept);
        }

        List<Transaction> toInsert = new ArrayList<>(rows.size());
        Set<Transaction> replaced = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Transaction> confirmed = new ArrayList<>();
        int installments = 0;
        for (Transaction row : rows) {
            Optional<InstallmentDetector.Installment> detected = row.getKind() == Transaction.Kind.REGULAR
                && row.getTransactionType() == Transaction.Type.DEBIT
                ? InstallmentDetector.detect(row.getOriginalDescription()) : Optional.empty();
            if (detected.isEmpty()) {
                toInsert.add(row);
                continue;
            }
            InstallmentDetector.Installment installment = detected.get();
            installments++;
            int number = installment.number();
            Group group = findGroup(groups.values(), installment, row);
            if (group == null) {
                group = new Group(UUID.randomUUID(), installment.total(), row.getAmount(),
                    installment.baseDescription());
                groups.put(group.id, group);
            }
            Transaction future = group.members.get(number);
            if (future != null && saved.contains(future)) {
                confirm(future, row);
                confirmed.add(future);
                continue;
            }
            if (future != null) {
                replaced.add(future);
            }
            row.setInstallment(true);
            row.setInstallmentNumber(number);
            row.setInstallmentTotal(installment.total());
            row.setInstallmentGroupId(group.id);
            row.setFutureInstallment(false);
            group.members.put(number, row);
            toInsert.add(row);
            for (int next = number + 1; next <= installment.total(); next++) {
                if (group.members.containsKey(next)) {
                    continue;
                }
                Transaction created = future(row, next);
                group.members.put(next, created);
                toInsert.add(created);
            }
        }
        List<Transaction> result = new ArrayList<>(toInsert.size());
        int futures = 0;
        for (Transaction transaction : toInsert) {
            if (!replaced.contains(transaction)) {
                result.add(transaction);
                if (transaction.isFutureInstallment()) {
                    futures++;
                }
            }
        }
        return new Plan(result, confirmed, installments, futures);
    }

    private static Group findGroup(Iterable<Group> groups, InstallmentDetector.Installment installment,
                                   Transaction row) {
        for (Group group : groups) {
            if (group.total != installment.total()
                || group.amount.subtract(row.getAmount()).abs().compareTo(amountTolerance(group.total)) > 0) {
                continue;
            }
            Transaction member = group.members.get(installment.number());
            if (member != null && !member.isFutureInstallment()) {
                continue;
            }
            if (nearExpectedDate(group, installment.number(), row.getTransactionDate())
                && DuplicateDetector.similar(group.baseDescription, installment.baseDescription())) {
                return group;
            }
        }
        return null;
    }

    /**
     * Decision 3A: the rounding cents of a purchase in {@code total} installments can all fall in one of them
     * (100,09 + 9 × 100,00), so the limit is the highest of R$ 0,05 and R$ 0,01 × (total − 1), inclusive.
     */
    static BigDecimal amountTolerance(int total) {
        return MIN_AMOUNT_TOLERANCE.max(AMOUNT_TOLERANCE_PER_INSTALLMENT.multiply(BigDecimal.valueOf(total - 1L)));
    }

    /** Decision 4A: the date expected for {@code number} comes from the member closest to it (the lowest on a tie). */
    private static boolean nearExpectedDate(Group group, int number, LocalDate date) {
        Transaction member = group.members.values().stream()
            .min(Comparator.<Transaction>comparingInt(t -> Math.abs(t.getInstallmentNumber() - number))
                .thenComparingInt(Transaction::getInstallmentNumber))
            .orElseThrow();
        LocalDate expected = member.getTransactionDate().plusMonths(number - member.getInstallmentNumber());
        return Math.abs(ChronoUnit.DAYS.between(expected, date)) <= MAX_DATE_DISTANCE_DAYS;
    }

    /** Decision D7: the future becomes the installment of the file. */
    private static void confirm(Transaction future, Transaction row) {
        future.setFutureInstallment(false);
        future.setExternalId(row.getExternalId());
        future.setTransactionDate(row.getTransactionDate());
        future.setAmount(row.getAmount());
        future.setDescription(row.getDescription());
        future.setOriginalDescription(row.getOriginalDescription());
        future.setPix(row.isPix());
        future.setPixDetails(row.getPixDetails());
    }

    /** The future installment {@code number} of {@code row}'s purchase, {@code number − row's number} months later. */
    private static Transaction future(Transaction row, int number) {
        Transaction future = new Transaction();
        future.setUserId(row.getUserId());
        future.setAccountId(row.getAccountId());
        future.setCreditCardId(row.getCreditCardId());
        future.setImportId(row.getImportId());
        future.setExternalId(null);
        future.setTransactionDate(row.getTransactionDate().plusMonths(number - row.getInstallmentNumber()));
        future.setDescription(clip(InstallmentDetector.withNumber(row.getDescription(), number)));
        future.setOriginalDescription(clip(InstallmentDetector.withNumber(row.getOriginalDescription(), number)));
        future.setAmount(row.getAmount());
        future.setTransactionType(row.getTransactionType());
        future.setKind(row.getKind());
        future.setClassificationSource(Transaction.ClassificationSource.NONE);
        future.setReviewStatus(row.getReviewStatus());
        future.setAffectsBalance(false);
        future.setPix(row.isPix());
        future.setPixDetails(row.getPixDetails());
        future.setInstallment(true);
        future.setInstallmentNumber(number);
        future.setInstallmentTotal(row.getInstallmentTotal());
        future.setInstallmentGroupId(row.getInstallmentGroupId());
        future.setFutureInstallment(true);
        return future;
    }

    private static String baseOf(String description) {
        return InstallmentDetector.detect(description).map(InstallmentDetector.Installment::baseDescription)
            .orElse(description == null ? "" : description);
    }

    private static String clip(String text) {
        return text.length() <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION);
    }

    /** The installments of one purchase, by number. */
    private static final class Group {
        private final UUID id;
        private final int total;
        private final BigDecimal amount;
        private final String baseDescription;
        private final Map<Integer, Transaction> members = new HashMap<>();

        private Group(UUID id, int total, BigDecimal amount, String baseDescription) {
            this.id = id;
            this.total = total;
            this.amount = amount;
            this.baseDescription = baseDescription;
        }
    }
}
