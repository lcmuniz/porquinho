package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.dto.AdminImportBankDetailResponse;
import com.porquinho.dto.AdminImportMetricsResponse;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionImportRepository.BankCounts;
import com.porquinho.repository.TransactionImportRepository.DayCounts;
import com.porquinho.repository.TransactionImportRepository.MessageCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportMetricsServiceTest {

    // 01:00 UTC on the 18th is still the 17th in America/Sao_Paulo (UTC-3)
    private static final Instant NOW = Instant.parse("2026-11-18T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private TransactionImportRepository repository;
    private ImportMetricsService service;

    record Bank(String getBank, long getSuccesses, long getFailures) implements BankCounts {
    }

    record Message(String getMessage, long getCount) implements MessageCount {
    }

    record Day(String getDay, long getSuccesses, long getFailures) implements DayCounts {
    }

    @BeforeEach
    void setUp() {
        repository = mock(TransactionImportRepository.class);
        service = new ImportMetricsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void errorTypeComesFromTheFixedMessages() {
        assertThat(ImportErrorType.of(TransactionImportService.UNKNOWN_CSV_MESSAGE)).isEqualTo(ImportErrorType.FORMAT);
        assertThat(ImportErrorType.of(TransactionImportProcessor.TRUNCATED_MESSAGE)).isEqualTo(ImportErrorType.FORMAT);
        assertThat(ImportErrorType.of(TransactionImportProcessor.NO_TRANSACTIONS_MESSAGE)).isEqualTo(ImportErrorType.PARSING);
        assertThat(ImportErrorType.of(TransactionImportProcessor.INTERRUPTED_MESSAGE)).isEqualTo(ImportErrorType.INTERRUPTED);
        assertThat(ImportErrorType.of(TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE)).isEqualTo(ImportErrorType.UNEXPECTED);
        assertThat(ImportErrorType.of("qualquer outra")).isEqualTo(ImportErrorType.OTHER);
        assertThat(ImportErrorType.of(null)).isEqualTo(ImportErrorType.OTHER);
    }

    @Test
    void rateIsNullWithoutFinishedImportsAndRedOnlyAbove5Percent() {
        assertThat(ImportMetricsService.rate(0, 0)).isNull();
        assertThat(ImportMetricsService.status(null)).isEqualTo(Status.UNKNOWN);
        assertThat(ImportMetricsService.status(5.0)).isEqualTo(Status.GREEN);
        assertThat(ImportMetricsService.status(5.01)).isEqualTo(Status.RED);
        assertThat(ImportMetricsService.rate(1, 19)).isEqualTo(5.0);
        // 5.004 % shows "5 %" next to the 5 % target: green, not red
        assertThat(ImportMetricsService.rate(5004, 94_996)).isEqualTo(5.0);
        assertThat(ImportMetricsService.status(ImportMetricsService.rate(5004, 94_996))).isEqualTo(Status.GREEN);
        assertThat(ImportMetricsService.rate(1, 2)).isEqualTo(33.3);
    }

    @Test
    void defaultPeriodIsTheLast30DaysUpToTodayInSaoPaulo() {
        ImportMetricsService.Filters f = service.filters(null, null, " ", "");
        assertThat(f).isEqualTo(new ImportMetricsService.Filters(TODAY.minusDays(29), TODAY, null, "ALL"));
    }

    @Test
    void invalidFiltersGive400() {
        assertThatThrownBy(() -> service.filters("2026-11-10", "2026-11-01", null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.filters("2025-01-01", "2026-01-02", null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.filters("2026-13-01", null, null, null))
            .isInstanceOf(BadRequestException.class).hasFieldOrPropertyWithValue("field", "from");
        assertThatThrownBy(() -> service.filters(null, "2026-11-31", null, null))
            .isInstanceOf(BadRequestException.class).hasFieldOrPropertyWithValue("field", "to");
        assertThatThrownBy(() -> service.filters(null, null, "SANTANDER", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.filters(null, null, null, "PROCESSING")).isInstanceOf(BadRequestException.class);
        // 366 days inclusive is the limit
        assertThat(service.filters("2025-01-01", "2026-01-01", "UNKNOWN", "FAILED").bank()).isEqualTo("UNKNOWN");
    }

    @Test
    void metricsCountsPerBankWithoutProcessingAndSortsByRate() {
        when(repository.countByBank(any(), any(), isNull())).thenReturn(List.of(
            new Bank("NUBANK", 19, 1), new Bank("ITAU", 0, 0), new Bank("UNKNOWN", 0, 2)));

        AdminImportMetricsResponse r = service.metrics(service.filters("2026-11-01", "2026-11-17", null, null));

        assertThat(r.target()).isEqualTo(5.0);
        assertThat(r.banks()).containsExactly(
            new AdminImportMetricsResponse.Bank("UNKNOWN", 2, 0, 2, 100.0, Status.RED),
            new AdminImportMetricsResponse.Bank("NUBANK", 20, 19, 1, 5.0, Status.GREEN),
            new AdminImportMetricsResponse.Bank("ITAU", 0, 0, 0, null, Status.UNKNOWN));
        // [from 00:00, to + 1 day 00:00) in America/Sao_Paulo
        verify(repository).countByBank(OffsetDateTime.parse("2026-11-01T00:00-03:00"),
            OffsetDateTime.parse("2026-11-18T00:00-03:00"), null);
    }

    @Test
    void detailGroupsErrorTypesAndFillsThe30DaysOfTheTrend() {
        when(repository.countFailuresByMessage(eq("NUBANK"), any(), any())).thenReturn(List.of(
            new Message(TransactionImportService.UNKNOWN_CSV_MESSAGE, 1),
            new Message(TransactionImportProcessor.TRUNCATED_MESSAGE, 1),
            new Message(TransactionImportProcessor.INTERRUPTED_MESSAGE, 1)));
        when(repository.countByDay(eq("NUBANK"), any(), any())).thenReturn(List.of(
            new Day(TODAY.toString(), 1, 3), new Day(TODAY.minusDays(29).toString(), 2, 0)));
        when(repository.findFinished(any(), any(), eq("NUBANK"), eq("FAILED"), eq(20))).thenReturn(List.of(
            failed("NUBANK", TransactionImportProcessor.INTERRUPTED_MESSAGE)));

        AdminImportBankDetailResponse d = service.detail(service.filters("2026-11-01", "2026-11-05", "NUBANK", "FAILED"));

        assertThat(d.errorTypes()).containsExactly(
            new AdminImportBankDetailResponse.ErrorTypeCount(ImportErrorType.FORMAT, 2),
            new AdminImportBankDetailResponse.ErrorTypeCount(ImportErrorType.INTERRUPTED, 1));
        assertThat(d.recent()).singleElement().satisfies(i -> {
            assertThat(i.errorType()).isEqualTo(ImportErrorType.INTERRUPTED);
            assertThat(i.message()).isEqualTo(TransactionImportProcessor.INTERRUPTED_MESSAGE);
        });
        assertThat(d.trend()).hasSize(30);
        assertThat(d.trend().get(0)).isEqualTo(new AdminImportBankDetailResponse.DayPoint(TODAY.minusDays(29), 2, 0, 0.0));
        assertThat(d.trend().get(1)).isEqualTo(new AdminImportBankDetailResponse.DayPoint(TODAY.minusDays(28), 0, 0, null));
        assertThat(d.trend().get(29)).isEqualTo(new AdminImportBankDetailResponse.DayPoint(TODAY, 4, 3, 75.0));
        // The trend ignores the chosen period
        verify(repository).countByDay("NUBANK", OffsetDateTime.parse("2026-10-19T00:00-03:00"),
            OffsetDateTime.parse("2026-11-18T00:00-03:00"));
    }

    @Test
    void detailNeedsABank() {
        assertThatThrownBy(() -> service.detail(service.filters(null, null, null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void csvIsPtBrWithBomSemicolonCrlfAndEscapes() {
        TransactionImport ok = failed(null, null);
        ok.setStatus(TransactionImport.Status.COMPLETED);
        ok.setCreatedAt(OffsetDateTime.parse("2026-11-17T02:30:00Z"));
        when(repository.findFinished(any(), any(), isNull(), eq("ALL"), anyInt())).thenReturn(List.of(
            failed("BANCO_DO_BRASIL", "=quebra; \"aspas\""), ok));

        String csv = service.csv(service.filters(null, null, null, null));

        assertThat(csv).isEqualTo("﻿Data;Banco;Tipo de arquivo;Status;Tipo de erro;Mensagem\r\n"
            + "17/11/2026 09:00;Banco do Brasil;OFX;Falhou;Outro;\"'=quebra; \"\"aspas\"\"\"\r\n"
            + "16/11/2026 23:30;Não identificado;OFX;Concluída;;\r\n");
    }

    @Test
    void csvWithoutImportsHasOnlyTheHeader() {
        when(repository.findFinished(any(), any(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        assertThat(service.csv(service.filters(null, null, "CAIXA", "SUCCESS")))
            .isEqualTo("﻿Data;Banco;Tipo de arquivo;Status;Tipo de erro;Mensagem\r\n");
    }

    private static TransactionImport failed(String bank, String message) {
        TransactionImport i = new TransactionImport(null, null, null, "extrato.ofx", TransactionImport.FileType.OFX, 1);
        i.setBankName(bank);
        i.setStatus(TransactionImport.Status.FAILED);
        i.setErrorMessage(message);
        i.setCreatedAt(OffsetDateTime.parse("2026-11-17T12:00:00Z"));
        return i;
    }
}
