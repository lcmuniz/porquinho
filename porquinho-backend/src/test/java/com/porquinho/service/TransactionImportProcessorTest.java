package com.porquinho.service;

import com.porquinho.repository.TransactionImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 5.3: the background processing (success, truncated, unexpected error) and the orphan sweeps. */
@ExtendWith(MockitoExtension.class)
class TransactionImportProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @Mock
    private TransactionImportRepository repository;

    private TransactionImportProcessor processor;
    private final UUID importId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        processor = new TransactionImportProcessor(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static byte[] sample(String name) throws IOException {
        return new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
    }

    @Test
    void detectedBankIsRecordedAndTheImportStaysProcessing() throws IOException {
        processor.process(importId, sample("nubank-conta.ofx"));

        verify(repository).recordBank(importId, "NUBANK", now);
        verify(repository, never()).markFailed(any(), anyString(), any());
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
