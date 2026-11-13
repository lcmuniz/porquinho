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
import org.springframework.transaction.support.SimpleTransactionStatus;

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
    private TransactionClassificationService classificationService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private BudgetBalanceCache budgetBalanceCache;
    @Mock
    private DashboardCache dashboardCache;

    private TransactionImportProcessor processor;
    private final UUID importId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final SimpleTransactionStatus transactionStatus = new SimpleTransactionStatus();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        processor = new TransactionImportProcessor(repository, transactionRepository, transferRepository,
            auditLogService, classificationService, budgetBalanceCache, dashboardCache, transactionManager,
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
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).recordBank(importId, "NUBANK", now);
        verify(repository).recordParsed(importId, 10, 10, 0, 0, 0, now);
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
            assertThat(t.isInstallment()).isFalse();
            assertThat(t.isFutureInstallment()).isFalse();
            // Story 5.6: details only on the Pix ones
            assertThat(t.getPixDetails() != null).isEqualTo(t.isPix());
        });
        assertThat(saved).filteredOn(Transaction::isPix).hasSize(4);
        assertThat(saved.get(0).getPixDetails()).isEqualTo("{\"detected\":true,\"direction\":\"RECEIVED\","
            + "\"counterpart_name\":\"JOANA PEREIRA LIMA\",\"document\":\"•••.432.198-••\",\"counterpart_bank\":"
            + "{\"name\":\"BCO DO BRASIL S.A.\",\"code\":\"0001\",\"agency\":\"1234\",\"account\":\"98765-4\"}}");
        assertThat(saved.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(saved.get(0).getAmount()).isEqualByComparingTo("2000.00");
        assertThat(saved.get(0).getTransactionType()).isEqualTo(Transaction.Type.CREDIT);
        assertThat(saved).filteredOn(t -> t.getKind() == Transaction.Kind.CARD_PAYMENT)
            .extracting(Transaction::getDescription).containsExactly("Pagamento da fatura - Cartão Nubank");
    }

    @Test
    void cardEntriesGoToTheCard() throws IOException {
        processingImport(null, cardId);
        when(repository.recordParsed(importId, 8, 8, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        List<Transaction> saved = savedTransactions();
        // Story 5.7: 8 entries plus the futures of 03/10 (7), 07/12 (5), 01/03 (2) and 05/12 (7)
        assertThat(saved).hasSize(29).filteredOn(t -> !t.isFutureInstallment()).hasSize(8);
        assertThat(saved).allSatisfy(t -> {
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
        when(repository.recordParsed(importId, 6, 2, 0, 0, 4, now)).thenReturn(1);

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
        verify(repository, never()).recordParsed(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void failureWhileSavingFailsWithTheGenericMessage() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);
        when(transactionRepository.saveAll(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"))).doesNotThrowAnyException();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(repository).markFailed(importId, TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE, now);
    }

    @Test
    void importThatLeftProcessingBeforeTheSaveGetsNothing() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(transactionRepository, never()).saveAll(any());
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    /** A future installment 03/10 of the card that itau-cartao.ofx confirms. */
    private void futureOfTheCard() {
        Transaction future = new Transaction();
        future.setId(UUID.randomUUID());
        future.setUserId(userId);
        future.setCreditCardId(cardId);
        future.setDescription("MAGAZINELUIZA 03/10");
        future.setOriginalDescription("MAGAZINELUIZA 03/10");
        future.setAmount(new BigDecimal("189.90"));
        future.setTransactionDate(java.time.LocalDate.of(2026, 10, 1));
        future.setTransactionType(Transaction.Type.DEBIT);
        future.setInstallment(true);
        future.setInstallmentNumber(3);
        future.setInstallmentTotal(10);
        future.setInstallmentGroupId(UUID.randomUUID());
        future.setFutureInstallment(true);
        when(transactionRepository.findActiveInstallmentsByCreditCard(userId, cardId)).thenReturn(List.of(future));
    }

    @Test
    void confirmedFutureIsRolledBackWhenTheImportLeftProcessing() throws IOException {
        processingImport(null, cardId);
        futureOfTheCard();
        when(repository.recordParsed(importId, 8, 7, 1, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        verify(transactionRepository, never()).saveAll(any());
        verify(budgetBalanceCache, never()).evict(any());
        verify(dashboardCache, never()).evictAll(any());
        assertThat(transactionStatus.isRollbackOnly()).isTrue();
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void savingAConfirmedFutureEvictsTheBudgetBalance() throws IOException {
        processingImport(null, cardId);
        futureOfTheCard();
        when(repository.recordParsed(importId, 8, 7, 1, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        verify(budgetBalanceCache).evict(userId);
        verify(dashboardCache).evictAll(userId);
    }

    @Test
    void anImportThatOnlyConfirmsAFutureEvictsTheDashboard() throws IOException {
        processingImport(null, cardId);
        futureOfTheCard();
        // every row but the 03/10 is already saved: nothing new, only the future confirmed
        List<Transaction> inDb = OfxTransactionParser.parse(sample("itau-cartao.ofx"), true).entries().stream()
            .filter(e -> !e.originalDescription().contains("03/10"))
            .map(TransactionImportProcessorTest::existing).toList();
        when(transactionRepository.findDuplicateCandidatesByCreditCardBetween(eq(userId), eq(cardId), any(), any()))
            .thenReturn(inDb);
        when(repository.markCompletedWithNothingNew(importId, 8, 8, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        verify(repository).markCompletedWithNothingNew(importId, 8, 8, 0, 0, now);
        verify(budgetBalanceCache).evict(userId);
        verify(dashboardCache).evictAll(userId);
    }

    @Test
    void importWithoutConfirmedFuturesDoesNotEvict() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(budgetBalanceCache, never()).evict(any());
        // the new rows still change the dashboard (7.7)
        verify(dashboardCache).evictAll(userId);
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
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);

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
        when(repository.recordParsed(importId, 9, 9, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.CSV, sample("nubank-cartao.csv"));

        verify(repository).recordBank(importId, "NUBANK", now);
        // Story 5.7: 9 entries plus the futures of Parcela 3/10 (7) and Parcela 2/12 (10)
        assertThat(savedTransactions()).hasSize(26).allSatisfy(t -> assertThat(t.getCreditCardId()).isEqualTo(cardId))
            .filteredOn(t -> !t.isFutureInstallment()).hasSize(9);
    }

    @ParameterizedTest
    @CsvSource({"bb-conta.csv, BANCO_DO_BRASIL, 10", "itau-conta.csv, ITAU, 10", "bradesco-conta.csv, BRADESCO, 9",
        "caixa-conta.csv, CAIXA, 10"})
    void eachBankCsvRecordsItsBank(String name, String bank, int entries) throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, entries, entries, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.CSV, sample(name));

        verify(repository).recordBank(importId, bank, now);
        assertThat(savedTransactions()).hasSize(entries);
    }

    @Test
    void csvSkippedRowsAreCounted() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 6, 2, 0, 0, 4, now)).thenReturn(1);

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
        when(transactionRepository.findDuplicateCandidatesByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 28))).thenReturn(inDb);
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).lockTarget(TransactionCore.lockKey(accountId));
        verify(transferRepository).findActiveByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 28));
        verify(repository, never()).recordParsed(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
        verify(transactionRepository, never()).saveAll(any());
        verify(auditLogService).log("import_duplicates_checked", userId, null, "{\"import_id\":\"" + importId
            + "\",\"total_transactions\":10,\"imported_transactions\":0,\"duplicates_found\":10,\"transfers_found\":0,"
            + "\"skipped_rows\":0,\"installments_found\":0,\"future_installments_created\":0,\"installments_confirmed\":0}");
        InOrder order = inOrder(repository, transactionRepository);
        order.verify(repository).lockTarget(TransactionCore.lockKey(accountId));
        order.verify(transactionRepository).findDuplicateCandidatesByAccountBetween(any(), any(), any(), any());
        order.verify(repository).markCompletedWithNothingNew(importId, 10, 10, 0, 0, now);
        verify(classificationService, never()).classify(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void partialDuplicatesAndTransferAreCountedAndTheRestSaved() throws IOException {
        processingImport(accountId, null);
        List<OfxTransactionParser.Entry> entries = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries();
        when(transactionRepository.findDuplicateCandidatesByAccountBetween(any(), any(), any(), any()))
            .thenReturn(List.of(existing(entries.get(1)), existing(entries.get(2))));
        com.porquinho.entity.Transfer transfer = new com.porquinho.entity.Transfer(UUID.randomUUID(), userId,
            UUID.randomUUID(), accountId, new BigDecimal("2000.00"), LocalDate.of(2026, 10, 1), null);
        when(transferRepository.findActiveByAccountBetween(any(), any(), any(), any())).thenReturn(List.of(transfer));
        when(repository.recordParsed(importId, 10, 7, 2, 1, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        List<Transaction> saved = savedTransactions();
        assertThat(saved).hasSize(7);
        // Story 5.6: the Pix dropped as transfer (Joana) and as duplicate (Carlos) create nothing
        assertThat(saved).filteredOn(Transaction::isPix).extracting(Transaction::getDescription)
            .allSatisfy(d -> assertThat(d).doesNotContain("JOANA", "CARLOS")).hasSize(2);
        verify(auditLogService).log(eq("import_duplicates_checked"), eq(userId), eq(null), anyString());
    }

    @Test
    void cardChecksOnlyTheCardAndNoTransfers() throws IOException {
        processingImport(null, cardId);
        when(repository.recordParsed(importId, 8, 8, 0, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        verify(transferRepository, never()).findActiveByAccountBetween(any(), any(), any(), any());
        InOrder order = inOrder(repository, transactionRepository);
        order.verify(repository).lockTarget(TransactionCore.lockKey(cardId));
        order.verify(transactionRepository).findDuplicateCandidatesByCreditCardBetween(eq(userId), eq(cardId), any(), any());
        order.verify(transactionRepository).findActiveInstallmentsByCreditCard(userId, cardId);
        order.verify(repository).recordParsed(importId, 8, 8, 0, 0, 0, now);
        order.verify(transactionRepository).saveAll(any());
    }

    @Test
    void skippedRowsGoToTheAuditMetadata() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 6, 2, 0, 0, 4, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("erro-ofx-linhas-invalidas.ofx"));

        verify(auditLogService).log("import_duplicates_checked", userId, null, "{\"import_id\":\"" + importId
            + "\",\"total_transactions\":6,\"imported_transactions\":2,\"duplicates_found\":0,\"transfers_found\":0,"
            + "\"skipped_rows\":4,\"installments_found\":0,\"future_installments_created\":0,\"installments_confirmed\":0}");
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
        when(transactionRepository.findDuplicateCandidatesByAccountBetween(userId, accountId, LocalDate.of(2026, 9, 30),
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
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(transactionRepository, never()).saveAll(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), anyString());
    }

    @Test
    void importThatLeftProcessingBeforeCompletingWithNothingNewWritesNothingNorAudit() throws IOException {
        processingImport(accountId, null);
        List<Transaction> inDb = OfxTransactionParser.parse(sample("nubank-conta.ofx"), false).entries().stream()
            .map(TransactionImportProcessorTest::existing).toList();
        when(transactionRepository.findDuplicateCandidatesByAccountBetween(any(), any(), any(), any())).thenReturn(inDb);
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository, never()).recordParsed(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
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
        when(transactionRepository.findDuplicateCandidatesByAccountBetween(any(), any(), any(), any())).thenReturn(inDb);
        when(repository.findByUserIdAndIdIn(userId, java.util.Set.of(csvImport))).thenReturn(List.of(csvOrigin));
        when(repository.markCompletedWithNothingNew(importId, 10, 10, 0, 0, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(repository).markCompletedWithNothingNew(importId, 10, 10, 0, 0, now);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void atStartupOrphansGoToReviewWithRowsAndFailWithout() {
        processor.failImportsInterruptedByRestart();

        verify(repository).reviewOrphansWithRows(now, now);
        verify(repository).failOrphansWithoutRows(now,
            "O processamento foi interrompido. Envie o arquivo de novo.", now);
    }

    @Test
    void everyFiveMinutesImportsWithNoChangeFor15MinutesAreSwept() {
        processor.failStaleImports();

        verify(repository).reviewOrphansWithRows(now.minusMinutes(15), now);
        verify(repository).failOrphansWithoutRows(now.minusMinutes(15),
            TransactionImportProcessor.INTERRUPTED_MESSAGE, now);
    }

    // Story 5.8

    @Test
    void theRowsAreClassifiedAfterTheCommitAndOnlyThenTheImportGoesToReview() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);
        when(repository.markReadyForReview(importId, now)).thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        InOrder order = inOrder(repository, transactionManager, classificationService);
        order.verify(repository).lockTarget(TransactionCore.lockKey(accountId));
        order.verify(repository).recordParsed(importId, 10, 10, 0, 0, 0, now);
        order.verify(transactionManager).commit(any());
        order.verify(classificationService).classify(importId, userId, false);
        order.verify(repository).markReadyForReview(importId, now);
    }

    @Test
    void rowsAreBornUnclassifiedAndCardPaymentsWithoutConfidence() throws IOException {
        processingImport(null, cardId);
        when(repository.recordParsed(eq(importId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), eq(now)))
            .thenReturn(1);

        processor.process(importId, TransactionImport.FileType.OFX, sample("itau-cartao.ofx"));

        List<Transaction> saved = savedTransactions();
        assertThat(saved).anyMatch(t -> t.getKind() == Transaction.Kind.CARD_PAYMENT);
        assertThat(saved).allSatisfy(t -> {
            assertThat(t.getCategoryId()).isNull();
            assertThat(t.getClassificationSource()).isEqualTo(Transaction.ClassificationSource.NONE);
            assertThat(t.getClassificationConfidence())
                .isEqualTo(t.getKind() == Transaction.Kind.CARD_PAYMENT ? null : Integer.valueOf(0));
        });
        verify(classificationService).classify(importId, userId, true);
    }

    @Test
    void importThatLeftProcessingAfterTheParsingIsNotClassified() throws IOException {
        processingImport(accountId, null);
        when(transactionRepository.findDuplicateCandidatesByAccountBetween(any(), any(), any(), any()))
            .thenReturn(java.util.Collections.emptyList());
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(0);

        processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx"));

        verify(classificationService, never()).classify(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(repository, never()).markReadyForReview(any(), any());
    }

    @Test
    void failureAfterTheCommitNeverFailsTheImport() throws IOException {
        processingImport(accountId, null);
        when(repository.recordParsed(importId, 10, 10, 0, 0, 0, now)).thenReturn(1);
        when(repository.markReadyForReview(importId, now)).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> processor.process(importId, TransactionImport.FileType.OFX, sample("nubank-conta.ofx")))
            .doesNotThrowAnyException();

        verify(repository, never()).markFailed(any(), anyString(), any());
    }
}
