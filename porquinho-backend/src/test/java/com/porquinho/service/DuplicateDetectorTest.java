package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.entity.Transfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 5.5: the duplicate and transfer check, covering the I/O matrix of the spec and the OFX × CSV samples. */
class DuplicateDetectorTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_2 = LocalDate.of(2026, 10, 2);

    private final UUID bb = UUID.randomUUID();
    private final UUID nubank = UUID.randomUUID();

    private static OfxTransactionParser.Entry entry(LocalDate date, String description, String amount,
                                                    Transaction.Type type, String externalId) {
        return OfxTransactionParser.entry(date, description, new BigDecimal(amount), type, externalId, false);
    }

    private static Transaction existing(LocalDate date, String description, String amount, Transaction.Type type,
                                        String externalId) {
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(date);
        transaction.setDescription(description);
        transaction.setOriginalDescription(description);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionType(type);
        transaction.setExternalId(externalId);
        return transaction;
    }

    private static Transaction saved(OfxTransactionParser.Entry entry) {
        return existing(entry.date(), entry.originalDescription(), entry.amount().toPlainString(), entry.type(),
            entry.externalId());
    }

    private Transfer transfer(UUID from, UUID to, String amount, LocalDate date) {
        return new Transfer(UUID.randomUUID(), UUID.randomUUID(), from, to, new BigDecimal(amount), date, null);
    }

    /** Existing transactions without import (no format): the format rule of decision 4A never applies. */
    private static DuplicateDetector.Result detect(List<OfxTransactionParser.Entry> entries, List<Transaction> existing,
                                                   List<Transfer> transfers, UUID accountId) {
        return DuplicateDetector.detect(entries, TransactionImport.FileType.OFX, existing, Map.of(), transfers,
            accountId);
    }

    /** {@code existing} came from an import of format {@code origin}; the file being imported is {@code fileType}. */
    private static DuplicateDetector.Result detect(List<OfxTransactionParser.Entry> entries,
                                                   TransactionImport.FileType fileType, List<Transaction> existing,
                                                   TransactionImport.FileType origin) {
        UUID originImport = UUID.randomUUID();
        existing.forEach(transaction -> transaction.setImportId(originImport));
        return DuplicateDetector.detect(entries, fileType, existing, Map.of(originImport, origin), List.of(), null);
    }

    private static byte[] sample(String name) throws IOException {
        return new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
    }

    @Test
    void sameExternalIdIsADuplicateWhateverTheRest() {
        var entries = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "A1"));
        var inDb = List.of(existing(OCT_2.plusDays(10), "Outra coisa", "99.00", Transaction.Type.CREDIT, "A1"));

        DuplicateDetector.Result result = detect(entries, inDb, List.of(), bb);

        assertThat(result.toSave()).isEmpty();
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.transfers()).isZero();
    }

    @Test
    void sameFileTwiceIsAllDuplicates() throws IOException {
        var entries = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries();
        var inDb = entries.stream().map(DuplicateDetectorTest::saved).toList();

        DuplicateDetector.Result result = detect(entries, inDb, List.of(), nubank);

        assertThat(result.toSave()).isEmpty();
        assertThat(result.duplicates()).isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource({"nubank-conta, 10", "bb-conta, 10", "itau-conta, 10", "bradesco-conta, 9", "caixa-conta, 10"})
    void ofxAndCsvOfTheSameMonthAreDuplicatesInBothOrders(String sample, int count) throws IOException {
        var ofx = OfxTransactionParser.parse(sample(sample + ".ofx"), false).entries();
        var csv = CsvStatementReader.parse(sample(sample + ".csv"), false).entries();
        assertThat(ofx).hasSize(count);
        assertThat(csv).hasSize(count);

        DuplicateDetector.Result csvAfterOfx = detect(csv, TransactionImport.FileType.CSV,
            ofx.stream().map(DuplicateDetectorTest::saved).toList(), TransactionImport.FileType.OFX);
        DuplicateDetector.Result ofxAfterCsv = detect(ofx, TransactionImport.FileType.OFX,
            csv.stream().map(DuplicateDetectorTest::saved).toList(), TransactionImport.FileType.CSV);

        assertThat(csvAfterOfx.toSave()).isEmpty();
        assertThat(csvAfterOfx.duplicates()).isEqualTo(count);
        assertThat(ofxAfterCsv.toSave()).isEmpty();
        assertThat(ofxAfterCsv.duplicates()).isEqualTo(count);
    }

    @Test
    void differentExternalIdsOfTheOtherFormatFallBackToSimilarity() {
        var entries = List.of(entry(OCT_2, "PAGAMENTO DE BOLETO SABESP", "98.32", Transaction.Type.DEBIT, "OFX-1"));

        assertThat(detect(entries, TransactionImport.FileType.OFX,
            List.of(existing(OCT_1, "Pagamento de Boleto - SABESP", "98.32", Transaction.Type.DEBIT, "90002")),
            TransactionImport.FileType.CSV).duplicates()).isEqualTo(1);
        assertThat(detect(entries, TransactionImport.FileType.CSV,
            List.of(existing(OCT_1, "Pagamento de Boleto - SABESP", "98.32", Transaction.Type.DEBIT, "90002")),
            TransactionImport.FileType.OFX).duplicates()).isEqualTo(1);
    }

    @Test
    void differentExternalIdsOfTheSameFormatAreDifferentTransactions() {
        // month turn (decision 4A): the October OFX has an UBER on 31/10, the November OFX another one on 01/11
        var entries = List.of(entry(LocalDate.of(2026, 11, 1), "UBER *TRIP", "23.70", Transaction.Type.DEBIT, "NOV-1"));
        var inDb = new ArrayList<>(List.of(
            existing(LocalDate.of(2026, 10, 31), "UBER *TRIP", "23.70", Transaction.Type.DEBIT, "OCT-9")));

        DuplicateDetector.Result result = detect(entries, TransactionImport.FileType.OFX, inDb,
            TransactionImport.FileType.OFX);

        assertThat(result.toSave()).hasSize(1);
        assertThat(result.duplicates()).isZero();
        assertThat(detect(entries, TransactionImport.FileType.CSV, List.of(
            existing(LocalDate.of(2026, 10, 31), "UBER *TRIP", "23.70", Transaction.Type.DEBIT, "OCT-9")),
            TransactionImport.FileType.CSV).toSave()).hasSize(1);
    }

    @Test
    void missingExternalIdOnEitherSideFallsBackToSimilarityWhateverTheFormat() {
        var withId = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "A1"));
        var withoutId = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null));

        assertThat(detect(withId, TransactionImport.FileType.OFX,
            List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null)), TransactionImport.FileType.OFX)
            .duplicates()).isEqualTo(1);
        assertThat(detect(withoutId, TransactionImport.FileType.CSV,
            List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "B7")), TransactionImport.FileType.CSV)
            .duplicates()).isEqualTo(1);
    }

    @Test
    void transactionWithoutImportOnlyMatchesBySimilarityWhenAnIdIsMissing() {
        // manual transaction (Story 5.13): no format, so different ids never fall back to similarity
        var inDb = List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "M1"));

        assertThat(detect(List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "A1")), inDb, List.of(), bb)
            .duplicates()).isZero();
        assertThat(detect(List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null)), inDb, List.of(), bb)
            .duplicates()).isEqualTo(1);
    }

    @Test
    void importWhoseFormatIsUnknownCountsAsNoFormat() {
        Transaction transaction = existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "M1");
        transaction.setImportId(UUID.randomUUID());

        assertThat(DuplicateDetector.detect(List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "A1")),
            TransactionImport.FileType.CSV, List.of(transaction), Map.of(), List.of(), bb).duplicates()).isZero();
    }

    @Test
    void twoEqualEntriesInTheFileWithNothingInTheDatabaseAreBothSaved() {
        var entries = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null),
            entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null));

        DuplicateDetector.Result result = detect(entries, List.of(), List.of(), bb);

        assertThat(result.toSave()).hasSize(2);
        assertThat(result.duplicates()).isZero();
    }

    @Test
    void partialReimportAbsorbsOneEntryPerExistingTransaction() {
        var entries = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null),
            entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null));
        var inDb = List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null));

        DuplicateDetector.Result result = detect(entries, inDb, List.of(), bb);

        assertThat(result.toSave()).hasSize(1);
        assertThat(result.duplicates()).isEqualTo(1);
    }

    @Test
    void sameExternalIdTwiceInTheFileOnlyOneIsADuplicate() {
        var entries = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "X"),
            entry(OCT_1, "Pão", "7.00", Transaction.Type.DEBIT, "X"));
        var inDb = List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "X"));

        DuplicateDetector.Result result = detect(entries, inDb, List.of(), bb);

        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.toSave()).extracting(OfxTransactionParser.Entry::description).containsExactly("Pão");
    }

    @Test
    void differentAmountTypeOrDateFartherThanOneDayIsNotADuplicate() {
        var inDb = List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null));

        assertThat(detect(List.of(entry(OCT_1, "Café", "5.01", Transaction.Type.DEBIT, null)),
            inDb, List.of(), bb).duplicates()).isZero();
        assertThat(detect(List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.CREDIT, null)),
            inDb, List.of(), bb).duplicates()).isZero();
        assertThat(detect(List.of(entry(OCT_1.plusDays(2), "Café", "5.00", Transaction.Type.DEBIT,
            null)), inDb, List.of(), bb).duplicates()).isZero();
        assertThat(detect(List.of(entry(OCT_2, "Café", "5.00", Transaction.Type.DEBIT, null)),
            inDb, List.of(), bb).duplicates()).isEqualTo(1);
    }

    @Test
    void differentDescriptionIsNotADuplicate() {
        var entries = List.of(entry(OCT_1, "Compra no débito - UBER *TRIP", "23.70", Transaction.Type.DEBIT, null));
        var inDb = List.of(existing(OCT_1, "Compra no débito - DROGASIL 1234", "23.70", Transaction.Type.DEBIT, null));

        assertThat(detect(entries, inDb, List.of(), bb).toSave()).hasSize(1);
    }

    @Test
    void amountScaleDoesNotMatter() {
        var entries = List.of(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null));
        var inDb = List.of(existing(OCT_1, "Café", "5", Transaction.Type.DEBIT, null));

        assertThat(detect(entries, inDb, List.of(), bb).duplicates()).isEqualTo(1);
    }

    @Test
    void eachEntryTakesTheClosestCandidateInDays() {
        // step 2: the 02/10 entry takes the 02/10 transaction (0 days), not the first in order (01/10); the 01/10 one is
        // then 2 days away from the 03/10 entry, which is saved
        LocalDate oct3 = LocalDate.of(2026, 10, 3);
        var entries = List.of(entry(OCT_2, "Café", "5.00", Transaction.Type.DEBIT, null),
            entry(oct3, "Café", "5.00", Transaction.Type.DEBIT, null));
        var inDb = List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, null),
            existing(OCT_2, "Café", "5.00", Transaction.Type.DEBIT, null));

        DuplicateDetector.Result similar = detect(entries, inDb, List.of(), bb);

        assertThat(similar.duplicates()).isEqualTo(1);
        assertThat(similar.toSave()).extracting(OfxTransactionParser.Entry::date).containsExactly(oct3);

        // step 3: same with transfers
        var pix = List.of(entry(OCT_2, "Pix", "2000.00", Transaction.Type.DEBIT, null),
            entry(oct3, "Pix", "2000.00", Transaction.Type.DEBIT, null));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1), transfer(bb, nubank, "2000.00", OCT_2));

        DuplicateDetector.Result transferred = detect(pix, List.of(), transfers, bb);

        assertThat(transferred.transfers()).isEqualTo(1);
        assertThat(transferred.duplicates()).isZero();
        assertThat(transferred.toSave()).extracting(OfxTransactionParser.Entry::date).containsExactly(oct3);
    }

    @Test
    void registeredTransferIsDroppedAndCountedApart() {
        var entries = List.of(entry(OCT_2, "Pix - Enviado", "2000.00", Transaction.Type.DEBIT, null));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        DuplicateDetector.Result result = detect(entries, List.of(), transfers, bb);

        assertThat(result.toSave()).isEmpty();
        assertThat(result.transfers()).isEqualTo(1);
        assertThat(result.duplicates()).isZero();
    }

    @Test
    void transferOnTheReceivingAccountMatchesACredit() {
        var entries = List.of(entry(OCT_1, "Transferência recebida", "2000.00", Transaction.Type.CREDIT, null));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        assertThat(detect(entries, List.of(), transfers, nubank).transfers()).isEqualTo(1);
    }

    @Test
    void transferInTheWrongDirectionIsSaved() {
        var entries = List.of(entry(OCT_2, "Pix - Recebido", "2000.00", Transaction.Type.CREDIT, null));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        DuplicateDetector.Result result = detect(entries, List.of(), transfers, bb);

        assertThat(result.toSave()).hasSize(1);
        assertThat(result.transfers()).isZero();
    }

    @Test
    void transferOfAnotherAmountOrTooFarIsSavedAndEachTransferAbsorbsOneEntry() {
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        assertThat(detect(List.of(entry(OCT_1, "Pix", "1999.99", Transaction.Type.DEBIT, null)),
            List.of(), transfers, bb).transfers()).isZero();
        assertThat(detect(List.of(entry(OCT_1.plusDays(2), "Pix", "2000.00", Transaction.Type.DEBIT,
            null)), List.of(), transfers, bb).transfers()).isZero();
        DuplicateDetector.Result two = detect(List.of(
            entry(OCT_1, "Pix", "2000.00", Transaction.Type.DEBIT, null),
            entry(OCT_1, "Pix", "2000.00", Transaction.Type.DEBIT, null)), List.of(), transfers, bb);
        assertThat(two.transfers()).isEqualTo(1);
        assertThat(two.toSave()).hasSize(1);
    }

    @Test
    void creditCardNeverChecksTransfers() {
        var entries = List.of(entry(OCT_1, "Pix", "2000.00", Transaction.Type.DEBIT, null));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        DuplicateDetector.Result result = detect(entries, List.of(), transfers, null);

        assertThat(result.toSave()).hasSize(1);
        assertThat(result.transfers()).isZero();
    }

    @Test
    void duplicateComesBeforeTransfer() {
        var entries = List.of(entry(OCT_1, "Pix - Enviado", "2000.00", Transaction.Type.DEBIT, "F1"));
        var inDb = List.of(existing(OCT_1, "Pix - Enviado", "2000.00", Transaction.Type.DEBIT, "F1"));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        DuplicateDetector.Result result = detect(entries, inDb, transfers, bb);

        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.transfers()).isZero();
    }

    @Test
    void countersAlwaysAddUpToTheEntries() {
        List<OfxTransactionParser.Entry> entries = new ArrayList<>();
        entries.add(entry(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "A"));
        entries.add(entry(OCT_1, "Pix", "2000.00", Transaction.Type.DEBIT, null));
        entries.add(entry(OCT_1, "Novo", "1.00", Transaction.Type.DEBIT, null));
        var inDb = List.of(existing(OCT_1, "Café", "5.00", Transaction.Type.DEBIT, "A"));
        var transfers = List.of(transfer(bb, nubank, "2000.00", OCT_1));

        DuplicateDetector.Result result = detect(entries, inDb, transfers, bb);

        assertThat(result.toSave().size() + result.duplicates() + result.transfers()).isEqualTo(entries.size());
        assertThat(result.toSave()).extracting(OfxTransactionParser.Entry::description).containsExactly("Novo");
    }

    @Test
    void normalizationDropsCaseAccentsAndPunctuation() {
        assertThat(DuplicateDetector.normalize("  Pix - Enviado - 01/10 09:15 Joana  Pereira  "))
            .isEqualTo("pix enviado 01 10 09 15 joana pereira");
        assertThat(DuplicateDetector.normalize("COMPRA COM CARTÃO • Ç")).isEqualTo("compra com cartao c");
        assertThat(DuplicateDetector.normalize(null)).isEmpty();
    }

    @Test
    void truncatedBbMemoIsSimilarThroughThePrefixRule() {
        String ofx = "PIX - ENVIADO 01/10 09:15 JOANA";
        String csv = "Pix - Enviado - 01/10 09:15 Joana Pereira Lima";
        String a = DuplicateDetector.normalize(ofx);
        String b = DuplicateDetector.normalize(csv);
        double levenshteinOnly = 1.0 - (double) DuplicateDetector.levenshtein(a, b) / Math.max(a.length(), b.length());

        assertThat(levenshteinOnly).isLessThan(DuplicateDetector.MIN_SIMILARITY);
        assertThat(DuplicateDetector.similar(ofx, csv)).isTrue();
    }

    @Test
    void shortPrefixAloneIsNotSimilar() {
        assertThat(DuplicateDetector.similar("Pix", "Pix enviado para Joana")).isFalse();
        assertThat(DuplicateDetector.similar("ENVIO PIX", "ENVIO PIX")).isTrue();
    }

    @Test
    void levenshteinSimilarityOfNinetyPercent() {
        // 1 edit in 10 characters: 0.90
        assertThat(DuplicateDetector.similar("abcdefghij", "abcdefghiX")).isTrue();
        // 2 edits in 10 characters: 0.80
        assertThat(DuplicateDetector.similar("abcdefghij", "abcdefghXY")).isFalse();
        assertThat(DuplicateDetector.levenshtein("kitten", "sitting")).isEqualTo(3);
    }
}
