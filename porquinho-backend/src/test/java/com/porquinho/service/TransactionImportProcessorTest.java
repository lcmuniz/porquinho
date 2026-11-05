package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stories 5.3 and 5.4: the background processing (bank, parsing, all-or-nothing save, REVIEW, failures) and the
 * orphan sweeps.
 */
@ExtendWith(MockitoExtension.class)
class TransactionImportProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @Mock
    private TransactionImportRepository repository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TransactionImportProcessor processor;
    private final UUID importId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        processor = new TransactionImportProcessor(repository, transactionRepository, transferRepository,
            auditLogService, transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static byte[] sample(String name) throws IOException {
        return new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
    }

    private void processingImport(UUID account, UUID card) {
        TransactionImport row = new TransactionImport(userId, account, card, "x.ofx", TransactionImport.FileType.OFX, 1);
        row.setId(importId);
        when(repository.recordBank(eq(importId), anyString(), eq(now))).thenReturn(1);
        when(repository.findById(importId)).thenReturn(Optional.of(row));
    }

    @SuppressWarnings("unchecked")
    private List<Transaction> savedTransactions() {
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void entriesAreSavedAsPendingAndTheImportGoesToReview() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).recordBank(importId, "NUBANK", now);
        verify(repository).markReadyForReview(importId, 10, 10, 0, 0, 0, now);
        verify(repository, never()).markFailed(any(), anyString(), any());
        List<Transaction> saved = savedTransactions();
        assertThat(saved).hasSize(10).allSatisfy(t -> {
            assertThat(t.getUserId()).isEqualTo(userId);
            assertThat(t.getAccountId()).isEqualTo(accountId);
            assertThat(t.getCreditCardId()).isNull();
            assertThat(t.getImportId()).isEqualTo(importId);
            assertThat(t.getReviewStatus()).isEqualTo(Transaction.ReviewStatus.PENDING);
            assertThat(t.getClassificationSource()).isEqualTo(Transaction.ClassificationSource.NONE);
            assertThat(t.isAffectsBalance()).isFalse();
            assertThat(t.isPix()).isFalse();
            assertThat(t.isInstallment()).isFalse();
            assertThat(t.isFutureInstallment()).isFalse();
        });
        assertThat(saved.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(saved.get(0).getAmount()).isEqualByComparingTo("2000.00");
        assertThat(saved.get(0).getTransactionType()).isEqualTo(Transaction.Type.CREDIT);
        assertThat(saved).filteredOn(t -> t.getKind() == Transaction.Kind.CARD_PAYMENT)
            .extracting(Transaction::getDescription).containsExactly("Pagamento da fatura - Cartão Nubank");
    }

    @Test
    void cardEntriesGoToTheCard() throws IOException {
        processingImport(null, cardId);
        when(repository.markReadyForReview(importId, 8, 8, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        List<Transaction> saved = savedTransactions();
        assertThat(saved).hasSize(8).allSatisfy(t -> {
            assertThat(t.getCreditCardId()).isEqualTo(cardId);
            assertThat(t.getAccountId()).isNull();
        });
        assertThat(saved).filteredOn(t -> t.getKind() == Transaction.Kind.CARD_PAYMENT)
            .extracting(Transaction::getTransactionType, Transaction::getAmount)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(Transaction.Type.CREDIT, new BigDecimal("1876.40")));
    }

    @Test
    void skippedRowsAreCountedAndTheValidOnesSaved() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 6, 2, 0, 0, 4, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("erro-ofx-linhas-invalidas.ofx"));

        assertThat(savedTransactions()).hasSize(2);
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void fileWithoutEntriesFailsWithTheNoTransactionsMessage() throws IOException {
        processingImport(accountId, null);
        Charset cp1252 = Charset.forName("windows-1252");
        byte[] empty = new String(sample("itau-conta.ofx"), cp1252)
            .replaceAll("(?s)<STMTTRN>.*?</STMTTRN>\\s*", "").getBytes(cp1252);

        processor.process(importId, TransactionImport.FileType.OFX, empty);

        verify(repository).markFailed(importId,
            "Não encontramos transações neste arquivo. Exporte o extrato de novo e envie outra vez.", now);
        verify(repository, never()).markReadyForReview(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void failureWhileSavingFailsWithTheGenericMessage() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);
        when(transactionRepository.saveAll(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"))).doesNotThrowAnyException();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(repository).markFailed(importId, TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE, now);
    }

    @Test
    void importThatLeftProcessingBeforeTheSaveGetsNothing() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(transactionRepository, never()).saveAll(any());
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void importThatLeftProcessingBeforeTheBankStopsThere() throws IOException {
        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).recordBank(importId, "NUBANK", now);
        verify(repository, never()).findById(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void truncatedFileFailsWithTheIncompleteMessage() throws IOException {
        processor.process(importId, TransactionImport.FileType.OFX, sample("erro-ofx-truncado.ofx"));

        verify(repository).markFailed(importId,
            "O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez.", now);
        verify(repository, never()).recordBank(any(), anyString(), any());
    }

    @Test
    void unexpectedErrorFailsWithTheGenericMessage() throws IOException {
        when(repository.recordBank(any(), anyString(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> processor.process(importId, TransactionImport.FileType.OFX, sample("bb-conta.ofx"))).doesNotThrowAnyException();

        verify(repository).markFailed(importId, "Não foi possível processar o arquivo. Tente de novo.", now);
    }

    @Test
    void nullContentAlsoFailsWithoutThrowing() {
        assertThatCode(() -> processor.process(importId, TransactionImport.FileType.OFX, null)).doesNotThrowAnyException();
        verify(repository).markFailed(eq(importId), eq(TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE), any());
    }

    @Test
    void csvTakesTheBankFromTheLayoutAndGoesToReview() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.CSV, sample("nubank-conta.csv"));

        verify(repository).recordBank(importId, "NUBANK", now);
        List<Transaction> saved = savedTransactions();
        assertThat(saved).hasSize(10).allSatisfy(t -> assertThat(t.getAccountId()).isEqualTo(accountId));
        assertThat(saved).filteredOn(t -> t.getKind() == Transaction.Kind.CARD_PAYMENT)
            .extracting(Transaction::getDescription).containsExactly("Pagamento da fatura - Cartão Nubank");
    }

    @Test
    void nubankCardCsvGoesToTheCard() throws IOException {
        processingImport(null, cardId);
        when(repository.markReadyForReview(importId, 9, 9, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.CSV, sample("nubank-cartao.csv"));

        verify(repository).recordBank(importId, "NUBANK", now);
        assertThat(savedTransactions()).hasSize(9).allSatisfy(t -> assertThat(t.getCreditCardId()).isEqualTo(cardId));
    }

    @ParameterizedTest
    @CsvSource({"bb-conta.csv, BANCO_DO_BRASIL, 10", "itau-conta.csv, ITAU, 10", "bradesco-conta.csv, BRADESCO, 9",
        "caixa-conta.csv, CAIXA, 10"})
    void eachBankCsvRecordsItsBank(String name, String bank, int entries) throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, entries, entries, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.CSV, sample(name));

        verify(repository).recordBank(importId, bank, now);
        assertThat(savedTransactions()).hasSize(entries);
    }

    @Test
    void csvSkippedRowsAreCounted() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 6, 2, 0, 0, 4, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.CSV, sample("erro-csv-linhas-invalidas.csv"));

        assertThat(savedTransactions()).hasSize(2);
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void csvOfUnknownLayoutFailsWithoutBank() throws IOException {
        processor.process(importId, TransactionImport.FileType.CSV, sample("erro-csv-banco-desconhecido.csv"));

        verify(repository).markFailed(importId, TransactionImportService.UNKNOWN_CSV_MESSAGE, now);
        verify(repository, never()).recordBank(any(), anyString(), any());
    }

    // Story 5.5

    private static Transaction existing(OfxTransactionParser.Entry entry) {
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(entry.date());
        transaction.setOriginalDescription(entry.originalDescription());
        transaction.setAmount(entry.amount());
        transaction.setTransactionType(entry.type());
        transaction.setExternalId(entry.externalId());
        return transaction;
    }

    @Test
    void sameFileAgainCompletesWithNothingNewAndAuditsOnlyCounts() throws IOException {
        processingImport(accountId, null);
        List<Transaction> inDb = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries().stream()
            .map(TransactionImportProcessorTest::existing).toList();
        when(transactionRepository.findActiveByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 28))).thenReturn(inDb);
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).lockTarget(TransactionImportProcessor.lockKey(accountId));
        verify(transferRepository).findActiveByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 28));
        verify(repository, never()).markReadyForReview(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
        verify(transactionRepository, never()).saveAll(any());
        verify(auditLogService).log("import_duplicates_checked", userId, null, "{\"import_id\":\"" + importId
            + "\",\"total_transactions\":10,\"imported_transactions\":0,\"duplicates_found\":10,\"transfers_found\":0,"
            + "\"skipped_rows\":0}");
        InOrder order = inOrder(repository, transactionRepository);
        order.verify(repository).lockTarget(TransactionImportProcessor.lockKey(accountId));
        order.verify(transactionRepository).findActiveByAccountBetween(any(), any(), any(), any());
        order.verify(repository).markCompletedWithNothingNew(importId, 10, 10, 0, 0, now);
    }

    @Test
    void partialDuplicatesAndTransferAreCountedAndTheRestSaved() throws IOException {
        processingImport(accountId, null);
        List<OfxTransactionParser.Entry> entries = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries();
        when(transactionRepository.findActiveByAccountBetween(any(), any(), any(), any()))
            .thenReturn(List.of(existing(entries.get(1)), existing(entries.get(2))));
        com.porquinho.entity.Transfer transfer = new com.porquinho.entity.Transfer(UUID.randomUUID(), userId,
            UUID.randomUUID(), accountId, new BigDecimal("2000.00"), LocalDate.of(2026, 10, 1), null);
        when(transferRepository.findActiveByAccountBetween(any(), any(), any(), any())).thenReturn(List.of(transfer));
        when(repository.markReadyForReview(importId, 10, 7, 2, 1, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        assertThat(savedTransactions()).hasSize(7);
        verify(auditLogService).log(eq("import_duplicates_checked"), eq(userId), eq(null), anyString());
    }

    @Test
    void cardChecksOnlyTheCardAndNoTransfers() throws IOException {
        processingImport(null, cardId);
        when(repository.markReadyForReview(importId, 8, 8, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        verify(transferRepository, never()).findActiveByAccountBetween(any(), any(), any(), any());
        InOrder order = inOrder(repository, transactionRepository);
        order.verify(repository).lockTarget(TransactionImportProcessor.lockKey(cardId));
        order.verify(transactionRepository).findActiveByCreditCardBetween(eq(userId), eq(cardId), any(), any());
        order.verify(repository).markReadyForReview(importId, 8, 8, 0, 0, 0, now);
        order.verify(transactionRepository).saveAll(any());
    }

    @Test
    void skippedRowsGoToTheAuditMetadata() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 6, 2, 0, 0, 4, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("erro-ofx-linhas-invalidas.ofx"));

        verify(auditLogService).log("import_duplicates_checked", userId, null, "{\"import_id\":\"" + importId
            + "\",\"total_transactions\":6,\"imported_transactions\":2,\"duplicates_found\":0,\"transfers_found\":0,"
            + "\"skipped_rows\":4}");
    }

    @Test
    void unsortedFileQueriesFromTheEarliestDateMinusOneToTheLatestPlusOne() throws IOException {
        // parsers keep the file order: the same statement with its entries in reverse date order
        String ofx = new String(sample("nubank-conta.ofx"), Charset.forName("windows-1252"));
        int start = ofx.indexOf("<STMTTRN>");
        int end = ofx.lastIndexOf("</STMTTRN>") + "</STMTTRN>".length();
        List<String> blocks = new java.util.ArrayList<>(List.of(ofx.substring(start, end).split("(?<=</STMTTRN>)\\s*")));
        java.util.Collections.reverse(blocks);
        byte[] reversed = (ofx.substring(0, start) + String.join("\n", blocks) + ofx.substring(end))
            .getBytes(Charset.forName("windows-1252"));
        List<OfxTransactionParser.Entry> entries = OfxTransactionParser.parse(reversed, false).entries();
        assertThat(entries.get(0).date()).isAfter(entries.get(entries.size() - 1).date());
        processingImport(accountId, null);
        List<Transaction> inDb = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries().stream()
            .map(TransactionImportProcessorTest::existing).toList();
        when(transactionRepository.findActiveByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 28))).thenReturn(inDb);
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, reversed);

        verify(transferRepository).findActiveByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 28));
        verify(repository).markCompletedWithNothingNew(importId, 10, 10, 0, 0, now);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void importThatLeftProcessingWritesNoCounterNorAudit() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(transactionRepository, never()).saveAll(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), anyString());
    }

    @Test
    void importThatLeftProcessingBeforeCompletingWithNothingNewWritesNothingNorAudit() throws IOException {
        processingImport(accountId, null);
        List<Transaction> inDb = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries().stream()
            .map(TransactionImportProcessorTest::existing).toList();
        when(transactionRepository.findActiveByAccountBetween(any(), any(), any(), any())).thenReturn(inDb);
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository, never()).markReadyForReview(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
        verify(transactionRepository, never()).saveAll(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), anyString());
    }

    @Test
    void differentIdsOfTheOtherFormatAreDuplicates() throws IOException {
        // decision 4A: the CSV entries already in the account came from a CSV import; the same month in OFX (other ids)
        // is all duplicates (the same-format case is covered in DuplicateDetectorTest)
        processingImport(accountId, null);
        UUID csvImport = UUID.randomUUID();
        TransactionImport csvOrigin = new TransactionImport(userId, accountId, null, "x.csv",
            TransactionImport.FileType.CSV, 1);
        csvOrigin.setId(csvImport);
        List<Transaction> inDb = CsvStatementReader.parse(sample("nubank-conta.csv"), false).entries().stream()
            .map(entry -> {
                Transaction transaction = existing(entry);
                transaction.setImportId(csvImport);
                return transaction;
            }).toList();
        when(transactionRepository.findActiveByAccountBetween(any(), any(), any(), any())).thenReturn(inDb);
        when(repository.findByUserIdAndIdIn(userId, java.util.Set.of(csvImport))).thenReturn(List.of(csvOrigin));
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).markCompletedWithNothingNew(importId, 10, 10, 0, 0, now);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void atStartupEveryImportCreatedBeforeTheBackendCameUpFails() {
        processor.failImportsInterruptedByRestart();

        verify(repository).failProcessingCreatedBefore(now,
            "O processamento foi interrompido. Envie o arquivo de novo.", now);
    }

    @Test
    void everyFiveMinutesImportsOlderThan15MinutesFail() {
        processor.failStaleImports();

        verify(repository).failProcessingCreatedBefore(now.minusMinutes(15),
            TransactionImportProcessor.INTERRUPTED_MESSAGE, now);
    }
}
