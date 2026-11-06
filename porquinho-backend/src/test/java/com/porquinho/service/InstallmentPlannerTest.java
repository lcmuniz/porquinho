package com.porquinho.service;

import com.porquinho.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 5.7: groups, future installments and confirmations of a card import. */
class InstallmentPlannerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID CARD = UUID.randomUUID();
    private static final UUID IMPORT = UUID.randomUUID();

    private static Transaction row(String description, String amount, LocalDate date, String externalId) {
        Transaction t = new Transaction();
        t.setUserId(USER);
        t.setCreditCardId(CARD);
        t.setImportId(IMPORT);
        t.setExternalId(externalId);
        t.setTransactionDate(date);
        t.setDescription(description);
        t.setOriginalDescription(description);
        t.setAmount(new BigDecimal(amount));
        t.setTransactionType(Transaction.Type.DEBIT);
        t.setKind(Transaction.Kind.REGULAR);
        t.setClassificationSource(Transaction.ClassificationSource.NONE);
        t.setReviewStatus(Transaction.ReviewStatus.PENDING);
        return t;
    }

    /** The saved rows of the first plan, as the repository would give them back (ids, oldest first). */
    private static List<Transaction> saved(InstallmentPlanner.Plan plan) {
        List<Transaction> saved = new ArrayList<>();
        for (Transaction t : plan.toInsert()) {
            t.setId(UUID.randomUUID());
            if (t.isInstallment()) {
                saved.add(t);
            }
        }
        return saved;
    }

    @Test
    void nubankInstallmentCreatesTheRemainingMonths() {
        Transaction row = row("Magazine Luiza - Parcela 3/10", "189.90", LocalDate.of(2026, 10, 2), "a");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(row), List.of());

        assertThat(plan.toInsert()).hasSize(8).first().isSameAs(row);
        assertThat(plan.installments()).isEqualTo(1);
        assertThat(plan.futuresCreated()).isEqualTo(7);
        assertThat(plan.confirmed()).isEmpty();
        assertThat(row.isInstallment()).isTrue();
        assertThat(row.isFutureInstallment()).isFalse();
        assertThat(row.getInstallmentNumber()).isEqualTo(3);
        assertThat(row.getInstallmentTotal()).isEqualTo(10);
        assertThat(plan.toInsert()).allSatisfy(t -> {
            assertThat(t.getInstallmentGroupId()).isEqualTo(row.getInstallmentGroupId()).isNotNull();
            assertThat(t.getAmount()).isEqualByComparingTo("189.90");
            assertThat(t.getImportId()).isEqualTo(IMPORT);
            assertThat(t.getReviewStatus()).isEqualTo(Transaction.ReviewStatus.PENDING);
        });
        List<Transaction> futures = plan.toInsert().subList(1, 8);
        assertThat(futures).allSatisfy(t -> {
            assertThat(t.isFutureInstallment()).isTrue();
            assertThat(t.getExternalId()).isNull();
        });
        assertThat(futures).extracting(Transaction::getInstallmentNumber).containsExactly(4, 5, 6, 7, 8, 9, 10);
        assertThat(futures).extracting(Transaction::getTransactionDate)
            .containsExactly(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 2), LocalDate.of(2027, 1, 2),
                LocalDate.of(2027, 2, 2), LocalDate.of(2027, 3, 2), LocalDate.of(2027, 4, 2), LocalDate.of(2027, 5, 2));
        assertThat(futures.get(0).getDescription()).isEqualTo("Magazine Luiza - Parcela 4/10");
        assertThat(futures.get(6).getDescription()).isEqualTo("Magazine Luiza - Parcela 10/10");
    }

    @Test
    void itauInstallmentKeepsTheBankDigitsInTheFutureDescription() {
        Transaction row = row("CASASBAHIA 07/12", "249.00", LocalDate.of(2026, 10, 3), "b");
        row.setOriginalDescription("CASASBAHIA     07/12");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(row), List.of());

        assertThat(plan.futuresCreated()).isEqualTo(5);
        assertThat(plan.toInsert().get(1).getDescription()).isEqualTo("CASASBAHIA 08/12");
        assertThat(plan.toInsert().get(1).getOriginalDescription()).isEqualTo("CASASBAHIA     08/12");
    }

    @Test
    void lastInstallmentCreatesNoFuture() {
        Transaction row = row("LOJA X 12/12", "50.00", LocalDate.of(2026, 10, 3), "c");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(row), List.of());

        assertThat(plan.toInsert()).containsExactly(row);
        assertThat(row.isInstallment()).isTrue();
        assertThat(plan.futuresCreated()).isZero();
    }

    @Test
    void invalidMarkersPaymentsAndRefundsAreNotInstallments() {
        Transaction invalid = row("LOJA 15/10", "10.00", LocalDate.of(2026, 10, 3), "d");
        Transaction refund = row("Estorno - LOJA 03/10", "10.00", LocalDate.of(2026, 10, 3), "e");
        refund.setTransactionType(Transaction.Type.CREDIT);
        Transaction payment = row("PAGAMENTO 03/10", "10.00", LocalDate.of(2026, 10, 3), "f");
        payment.setKind(Transaction.Kind.CARD_PAYMENT);
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(invalid, refund, payment), List.of());

        assertThat(plan.toInsert()).containsExactly(invalid, refund, payment);
        assertThat(plan.toInsert()).noneMatch(Transaction::isInstallment);
        assertThat(plan.installments()).isZero();
    }

    @Test
    void nextMonthConfirmsTheSavedFutureInsteadOfInsertingAnother() {
        InstallmentPlanner.Plan first = InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of());
        List<Transaction> existing = saved(first);
        Transaction future4 = existing.get(1);

        Transaction next = row("MAGAZINELUIZA 04/10", "189.90", LocalDate.of(2026, 11, 3), "n");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(next), existing);

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.confirmed()).containsExactly(future4);
        assertThat(plan.installments()).isEqualTo(1);
        assertThat(plan.futuresCreated()).isZero();
        assertThat(future4.isFutureInstallment()).isFalse();
        assertThat(future4.getExternalId()).isEqualTo("n");
        assertThat(future4.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(future4.getImportId()).isEqualTo(IMPORT);
    }

    @Test
    void olderMonthImportedLaterJoinsTheGroupWithoutFutures() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction older = row("MAGAZINELUIZA 02/10", "189.90", LocalDate.of(2026, 9, 1), "o");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(older), existing);

        assertThat(plan.toInsert()).containsExactly(older);
        assertThat(plan.futuresCreated()).isZero();
        assertThat(older.getInstallmentGroupId()).isEqualTo(existing.get(0).getInstallmentGroupId());
        assertThat(older.isFutureInstallment()).isFalse();
    }

    @Test
    void anotherPurchaseOrAnotherAmountOpensItsOwnGroup() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction otherShop = row("CASASBAHIA 04/10", "189.90", LocalDate.of(2026, 11, 1), "x");
        Transaction otherAmount = row("MAGAZINELUIZA 04/10", "99.90", LocalDate.of(2026, 11, 1), "y");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(otherShop, otherAmount), existing);

        assertThat(plan.confirmed()).isEmpty();
        assertThat(plan.futuresCreated()).isEqualTo(12);
        assertThat(otherShop.getInstallmentGroupId()).isNotEqualTo(existing.get(0).getInstallmentGroupId())
            .isNotEqualTo(otherAmount.getInstallmentGroupId());
        assertThat(otherAmount.getInstallmentGroupId()).isNotEqualTo(existing.get(0).getInstallmentGroupId());
    }

    @Test
    void severalMonthsOfOnePurchaseInOneFileKeepOneRowPerNumber() {
        Transaction may = row("MAGAZINELUIZA 05/10", "189.90", LocalDate.of(2026, 12, 1), "m");
        Transaction march = row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a");
        Transaction april = row("MAGAZINELUIZA 04/10", "189.90", LocalDate.of(2026, 11, 1), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(may, march, april), List.of());

        assertThat(plan.confirmed()).isEmpty();
        assertThat(plan.installments()).isEqualTo(3);
        assertThat(plan.toInsert()).extracting(Transaction::getInstallmentNumber)
            .containsExactlyInAnyOrder(3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(plan.toInsert()).extracting(Transaction::getInstallmentGroupId).containsOnly(may.getInstallmentGroupId());
        assertThat(plan.toInsert()).filteredOn(Transaction::isFutureInstallment)
            .extracting(Transaction::getInstallmentNumber).containsExactly(6, 7, 8, 9, 10);
        assertThat(plan.futuresCreated()).isEqualTo(5);
    }

    @Test
    void sameNumberAlreadyConfirmedOpensAnotherGroup() {
        // two equal purchases: 3/10 of each in the same month
        Transaction a = row("LOJA X 03/10", "100.00", LocalDate.of(2026, 10, 1), "a");
        Transaction b = row("LOJA X 03/10", "100.00", LocalDate.of(2026, 10, 2), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(a, b), List.of());

        assertThat(a.getInstallmentGroupId()).isNotEqualTo(b.getInstallmentGroupId());
        assertThat(plan.futuresCreated()).isEqualTo(14);
    }

    @Test
    void futureDatesFollowTheCalendar() {
        Transaction row = row("LOJA X 01/03", "10.00", LocalDate.of(2027, 1, 31), "a");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(row), List.of());

        assertThat(plan.toInsert()).extracting(Transaction::getTransactionDate)
            .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
    }

    @Test
    void confirmingCreatesNoFutureEvenWithANumberMissing() {
        List<Transaction> existing = new ArrayList<>(saved(InstallmentPlanner.plan(
            List.of(row("LOJA X 03/10", "100.00", LocalDate.of(2026, 10, 1), "a")), List.of())));
        existing.removeIf(t -> t.getInstallmentNumber() == 7); // soft-deleted: not in the active list
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(
            List.of(row("LOJA X 04/10", "100.00", LocalDate.of(2026, 11, 1), "n")), existing);

        assertThat(plan.confirmed()).hasSize(1);
        assertThat(plan.toInsert()).isEmpty();
    }

    @Test
    void insertedRowCreatesOnlyTheMissingNumbersAfterIt() {
        // group 3..10 without 9 and 10 (deleted); the file brings 2/10
        List<Transaction> existing = new ArrayList<>(saved(InstallmentPlanner.plan(
            List.of(row("LOJA X 03/10", "100.00", LocalDate.of(2026, 10, 1), "a")), List.of())));
        existing.removeIf(t -> t.getInstallmentNumber() >= 9);
        Transaction older = row("LOJA X 02/10", "100.00", LocalDate.of(2026, 9, 1), "o");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(older), existing);

        assertThat(plan.toInsert()).first().isSameAs(older);
        assertThat(plan.toInsert()).filteredOn(Transaction::isFutureInstallment)
            .extracting(Transaction::getInstallmentNumber, Transaction::getTransactionDate)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(9, LocalDate.of(2027, 4, 1)),
                org.assertj.core.groups.Tuple.tuple(10, LocalDate.of(2027, 5, 1)));
        assertThat(plan.futuresCreated()).isEqualTo(2);
    }

    @Test
    void roundingCentsStillConfirmTheFuture() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MERCADOLIVRE*LOJA 01/03", "333.34", LocalDate.of(2026, 10, 8), "a")), List.of()));
        Transaction second = row("MERCADOLIVRE*LOJA 02/03", "333.33", LocalDate.of(2026, 11, 8), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(second), existing);

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.confirmed()).singleElement().satisfies(t -> {
            assertThat(t.getInstallmentNumber()).isEqualTo(2);
            assertThat(t.getAmount()).isEqualByComparingTo("333.33");
            assertThat(t.isFutureInstallment()).isFalse();
        });
    }

    @ParameterizedTest
    @CsvSource({"2, 0.05", "3, 0.05", "6, 0.05", "7, 0.06", "10, 0.09", "12, 0.11", "48, 0.47"})
    void toleranceIsTheHighestOfFiveCentsAndOneCentPerInstallmentAfterTheFirst(int total, String tolerance) {
        assertThat(InstallmentPlanner.amountTolerance(total)).isEqualByComparingTo(tolerance);
    }

    @Test
    void fiveCentsIsTheLimitOfASmallTotal() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("LOJA X 01/03", "100.00", LocalDate.of(2026, 10, 1), "a")), List.of()));
        InstallmentPlanner.Plan within = InstallmentPlanner.plan(
            List.of(row("LOJA X 02/03", "100.05", LocalDate.of(2026, 11, 1), "b")), existing);
        assertThat(within.confirmed()).hasSize(1);

        List<Transaction> fresh = saved(InstallmentPlanner.plan(
            List.of(row("LOJA X 01/03", "100.00", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction beyond = row("LOJA X 02/03", "100.06", LocalDate.of(2026, 11, 1), "c");
        InstallmentPlanner.Plan outside = InstallmentPlanner.plan(List.of(beyond), fresh);
        assertThat(outside.confirmed()).isEmpty();
        assertThat(beyond.getInstallmentGroupId()).isNotEqualTo(fresh.get(0).getInstallmentGroupId());
    }

    @Test
    void biggerRemainderStillConfirmsTheFuture() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("LOJA X 01/10", "100.09", LocalDate.of(2026, 10, 1), "a")), List.of()));
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(
            List.of(row("LOJA X 02/10", "100.00", LocalDate.of(2026, 11, 1), "b")), existing);

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.confirmed()).singleElement().satisfies(t -> {
            assertThat(t.getInstallmentNumber()).isEqualTo(2);
            assertThat(t.getAmount()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void anAmountBeyondTheToleranceOpensItsOwnGroup() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction other = row("MAGAZINELUIZA 04/10", "190.00", LocalDate.of(2026, 11, 1), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(other), existing);

        assertThat(plan.confirmed()).isEmpty();
        assertThat(other.getInstallmentGroupId()).isNotEqualTo(existing.get(0).getInstallmentGroupId());
        assertThat(plan.futuresCreated()).isEqualTo(6);
    }

    @Test
    void anotherTotalOpensItsOwnGroup() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction other = row("MAGAZINELUIZA 04/12", "189.90", LocalDate.of(2026, 11, 1), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(other), existing);

        assertThat(plan.confirmed()).isEmpty();
        assertThat(other.getInstallmentGroupId()).isNotEqualTo(existing.get(0).getInstallmentGroupId());
        assertThat(plan.toInsert()).filteredOn(Transaction::isFutureInstallment)
            .extracting(Transaction::getInstallmentNumber).containsExactly(5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void confirmationKeepsTheStatusAndTheCategory() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction future4 = existing.get(1);
        UUID category = UUID.randomUUID();
        future4.setReviewStatus(Transaction.ReviewStatus.CONFIRMED);
        future4.setCategoryId(category);
        future4.setClassificationSource(Transaction.ClassificationSource.USER);
        Transaction next = row("MAGAZINELUIZA 04/10", "189.90", LocalDate.of(2026, 11, 1), "n");
        next.setCategoryId(UUID.randomUUID());
        next.setClassificationSource(Transaction.ClassificationSource.AI);
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(next), existing);

        assertThat(plan.confirmed()).containsExactly(future4);
        assertThat(future4.getReviewStatus()).isEqualTo(Transaction.ReviewStatus.CONFIRMED);
        assertThat(future4.getCategoryId()).isEqualTo(category);
        assertThat(future4.getClassificationSource()).isEqualTo(Transaction.ClassificationSource.USER);
    }

    @Test
    void confirmationKeepsWhatTheFileBrings() {
        Transaction first = row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a");
        first.setPix(true);
        first.setPixDetails("detalhes da futura");
        List<Transaction> existing = saved(InstallmentPlanner.plan(List.of(first), List.of()));
        Transaction future4 = existing.get(1);
        Transaction next = row("MAGAZINE LUIZA 04/10", "189.91", LocalDate.of(2026, 11, 3), "n");
        next.setOriginalDescription("MAGAZINE  LUIZA  04/10");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(next), existing);

        assertThat(plan.confirmed()).containsExactly(future4);
        assertThat(future4.getDescription()).isEqualTo("MAGAZINE LUIZA 04/10");
        assertThat(future4.getOriginalDescription()).isEqualTo("MAGAZINE  LUIZA  04/10");
        assertThat(future4.getAmount()).isEqualByComparingTo("189.91");
        assertThat(future4.isPix()).isFalse();
        assertThat(future4.getPixDetails()).isNull();

        Transaction pix = row("MAGAZINELUIZA 05/10", "189.90", LocalDate.of(2026, 12, 1), "p");
        pix.setPix(true);
        pix.setPixDetails("detalhes do arquivo");
        Transaction future5 = InstallmentPlanner.plan(List.of(pix), existing).confirmed().get(0);
        assertThat(future5.isPix()).isTrue();
        assertThat(future5.getPixDetails()).isEqualTo("detalhes do arquivo");
    }

    @Test
    void groupIsFoundByTheOriginalDescriptionEvenAfterAnEdit() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        existing.forEach(t -> t.setDescription("Geladeira nova")); // edited by the user (Story 5.12)
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 04/10", "189.90", LocalDate.of(2026, 11, 1), "n")), existing);

        assertThat(plan.confirmed()).hasSize(1);
        assertThat(plan.toInsert()).isEmpty();
    }

    @Test
    void repeatedPurchaseOfAnotherYearOpensItsOwnGroup() {
        // group 5..12 of 2026; next year's annual fee has number 1 free in it, but the date is a year away
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("ANUIDADE DIFERENCIADA 05/12", "32.00", LocalDate.of(2026, 10, 25), "a")), List.of()));
        Transaction nextYear = row("ANUIDADE DIFERENCIADA 01/12", "32.00", LocalDate.of(2027, 6, 25), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(nextYear), existing);

        assertThat(plan.confirmed()).isEmpty();
        assertThat(nextYear.getInstallmentGroupId()).isNotEqualTo(existing.get(0).getInstallmentGroupId());
        assertThat(plan.toInsert()).filteredOn(Transaction::isFutureInstallment)
            .extracting(Transaction::getInstallmentNumber).containsExactly(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(plan.toInsert()).extracting(Transaction::getInstallmentGroupId)
            .containsOnly(nextYear.getInstallmentGroupId());
    }

    @Test
    void repeatedPurchaseKeepsConfirmingItsOwnGroup() {
        // both purchases saved: the old group has 12/12 taken, so the new 12/12 confirms the future of the new group
        List<Transaction> existing = new ArrayList<>(saved(InstallmentPlanner.plan(
            List.of(row("ANUIDADE DIFERENCIADA 12/12", "32.00", LocalDate.of(2027, 5, 25), "a")), List.of())));
        existing.addAll(saved(InstallmentPlanner.plan(
            List.of(row("ANUIDADE DIFERENCIADA 01/12", "32.00", LocalDate.of(2027, 6, 25), "b")), existing)));
        Transaction may = row("ANUIDADE DIFERENCIADA 12/12", "32.00", LocalDate.of(2028, 5, 25), "c");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(may), existing);

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.confirmed()).singleElement().satisfies(t -> {
            assertThat(t.getInstallmentNumber()).isEqualTo(12);
            assertThat(t.getInstallmentGroupId()).isEqualTo(existing.get(1).getInstallmentGroupId());
        });
    }

    @Test
    void aDateNearTheExpectedOneConfirmsTheFuture() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction near = row("MAGAZINELUIZA 04/10", "189.90", LocalDate.of(2026, 11, 15), "b");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(near), existing);

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.confirmed()).singleElement().satisfies(t -> {
            assertThat(t.getInstallmentNumber()).isEqualTo(4);
            assertThat(t.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        });
    }

    @Test
    void twentyDaysIsTheLimitOnBothSides() {
        for (LocalDate limit : List.of(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 10, 12))) {
            List<Transaction> existing = saved(InstallmentPlanner.plan(
                List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
            InstallmentPlanner.Plan plan = InstallmentPlanner.plan(
                List.of(row("MAGAZINELUIZA 04/10", "189.90", limit, "b")), existing);
            assertThat(plan.confirmed()).hasSize(1);
        }
        for (LocalDate beyond : List.of(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 10, 11))) {
            List<Transaction> existing = saved(InstallmentPlanner.plan(
                List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
            Transaction row = row("MAGAZINELUIZA 04/10", "189.90", beyond, "b");
            InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(row), existing);
            assertThat(plan.confirmed()).isEmpty();
            assertThat(row.getInstallmentGroupId()).isNotEqualTo(existing.get(0).getInstallmentGroupId());
        }
    }

    @Test
    void numberBaseAndFuturesComeFromTheOriginalDescription() {
        List<Transaction> existing = saved(InstallmentPlanner.plan(
            List.of(row("MAGAZINELUIZA 03/10", "189.90", LocalDate.of(2026, 10, 1), "a")), List.of()));
        Transaction row = row("MAGAZINELUIZA 02/10", "189.90", LocalDate.of(2026, 11, 1), "b");
        row.setOriginalDescription("MAGAZINELUIZA  04/10");
        InstallmentPlanner.Plan plan = InstallmentPlanner.plan(List.of(row), existing);

        assertThat(plan.confirmed()).singleElement()
            .satisfies(t -> assertThat(t.getInstallmentNumber()).isEqualTo(4));

        Transaction markerOnlyInTheDescription = row("LOJA X 03/10", "10.00", LocalDate.of(2026, 10, 1), "c");
        markerOnlyInTheDescription.setOriginalDescription("LOJA X");
        InstallmentPlanner.Plan none = InstallmentPlanner.plan(List.of(markerOnlyInTheDescription), List.of());
        assertThat(none.installments()).isZero();
        assertThat(none.toInsert()).containsExactly(markerOnlyInTheDescription);
    }
}
