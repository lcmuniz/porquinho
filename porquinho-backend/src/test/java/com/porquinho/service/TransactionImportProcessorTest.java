package com.porquinho.service;

import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PlatformTransactionManager transactionManager;

    private TransactionImportProcessor processor;
    private final UUID importId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        processor = new TransactionImportProcessor(repository, transactionRepository, transactionManager,
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
        when(repository.markReadyForReview(importId, 10, 10, 0, now)).thenReturn(1);

        processor.process(importId, sample("nubank-conta.ofx"));

        verify(repository).recordBank(importId, "NUBANK", now);
        verify(repository).markReadyForReview(importId, 10, 10, 0, now);
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
        when(repository.markReadyForReview(importId, 8, 8, 0, now)).thenReturn(1);

        processor.process(importId, sample("itau-cartao.ofx"));

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
        when(repository.markReadyForReview(importId, 6, 2, 4, now)).thenReturn(1);

        processor.process(importId, sample("erro-ofx-linhas-invalidas.ofx"));

        assertThat(savedTransactions()).hasSize(2);
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void fileWithoutEntriesFailsWithTheNoTransactionsMessage() throws IOException {
        processingImport(accountId, null);
        Charset cp1252 = Charset.forName("windows-1252");
        byte[] empty = new String(sample("itau-conta.ofx"), cp1252)
            .replaceAll("(?s)<STMTTRN>.*?</STMTTRN>\\s*", "").getBytes(cp1252);

        processor.process(importId, empty);

        verify(repository).markFailed(importId,
            "Não encontramos transações neste arquivo. Exporte o extrato de novo e envie outra vez.", now);
        verify(repository, never()).markReadyForReview(any(), anyInt(), anyInt(), anyInt(), any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void failureWhileSavingFailsWithTheGenericMessage() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, now)).thenReturn(1);
        when(transactionRepository.saveAll(any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> processor.process(importId, sample("nubank-conta.ofx"))).doesNotThrowAnyException();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(repository).markFailed(importId, TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE, now);
    }

    @Test
    void importThatLeftProcessingBeforeTheSaveGetsNothing() throws IOException {
        processingImport(accountId, null);
        when(repository.markReadyForReview(importId, 10, 10, 0, now)).thenReturn(0);

        processor.process(importId, sample("nubank-conta.ofx"));

        verify(transactionRepository, never()).saveAll(any());
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void importThatLeftProcessingBeforeTheBankStopsThere() throws IOException {
        processor.process(importId, sample("nubank-conta.ofx"));

        verify(repository).recordBank(importId, "NUBANK", now);
        verify(repository, never()).findById(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void truncatedFileFailsWithTheIncompleteMessage() throws IOException {
        processor.process(importId, sample("erro-ofx-truncado.ofx"));

        verify(repository).markFailed(importId,
            "O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez.", now);
        verify(repository, never()).recordBank(any(), anyString(), any());
    }

    @Test
    void unexpectedErrorFailsWithTheGenericMessage() throws IOException {
        when(repository.recordBank(any(), anyString(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> processor.process(importId, sample("bb-conta.ofx"))).doesNotThrowAnyException();

        verify(repository).markFailed(importId, "Não foi possível processar o arquivo. Tente de novo.", now);
    }

    @Test
    void nullContentAlsoFailsWithoutThrowing() {
        assertThatCode(() -> processor.process(importId, null)).doesNotThrowAnyException();
        verify(repository).markFailed(eq(importId), eq(TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE), any());
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
