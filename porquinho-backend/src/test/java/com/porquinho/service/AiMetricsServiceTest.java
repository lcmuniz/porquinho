package com.porquinho.service;

import com.porquinho.dto.AdminAiBankDetailResponse;
import com.porquinho.dto.AdminAiBankDetailResponse.Group;
import com.porquinho.dto.AdminAiMetricsResponse;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.AiClassificationHistoryRepository.BankCounts;
import com.porquinho.repository.AiClassificationHistoryRepository.KindCounts;
import com.porquinho.repository.AiClassificationHistoryRepository.MonthCounts;
import com.porquinho.repository.UserClassificationPatternRepository;
import com.porquinho.repository.UserClassificationPatternRepository.SharedPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiMetricsServiceTest {

    // 01:00 UTC on December 1st is still November 30th in America/Sao_Paulo (UTC-3)
    private static final Instant NOW = Instant.parse("2026-12-01T01:00:00Z");

    private AiClassificationHistoryRepository history;
    private UserClassificationPatternRepository patterns;
    private AiMetricsService service;
    private ImportMetricsService importMetrics;

    record Bank(String getBank, long getClassifications, long getCorrections) implements BankCounts {
    }

    record Month(String getBank, String getMonth, long getClassifications, long getCorrections) implements MonthCounts {
    }

    record Kind(String getType, String getBand, long getClassifications, long getCorrections) implements KindCounts {
    }

    record Shared(String getPattern, long getUsers, long getUsage) implements SharedPattern {
    }

    @BeforeEach
    void setUp() {
        history = mock(AiClassificationHistoryRepository.class);
        patterns = mock(UserClassificationPatternRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AiMetricsService(history, patterns, clock);
        importMetrics = new ImportMetricsService(null, clock);
    }

    @Test
    void accuracyIsNullWithoutClassificationsAndColorsAt75And85() {
        assertThat(AiMetricsService.accuracy(0, 0)).isNull();
        assertThat(AiMetricsService.accuracy(20, 3)).isEqualTo(85.0);
        assertThat(AiMetricsService.status(null)).isEqualTo(Status.UNKNOWN);
        assertThat(AiMetricsService.status(85.0)).isEqualTo(Status.GREEN);
        assertThat(AiMetricsService.status(84.99)).isEqualTo(Status.YELLOW);
        assertThat(AiMetricsService.status(80.0)).isEqualTo(Status.YELLOW);
        assertThat(AiMetricsService.status(75.0)).isEqualTo(Status.YELLOW);
        assertThat(AiMetricsService.status(74.99)).isEqualTo(Status.RED);
    }

    @Test
    void metricsSortsWorstFirstFillsSixMonthsAndHasNoComparisonWithoutThePreviousMonth() {
        when(history.countByBank(any(), any(), isNull())).thenReturn(List.of(
            new Bank("NUBANK", 10, 1), new Bank("ITAU", 10, 4)));
        when(history.countByBankAndMonth(any(), any())).thenReturn(List.of(
            new Month("NUBANK", "2026-11", 10, 1), new Month("ITAU", "2026-07", 4, 1)));

        AdminAiMetricsResponse r = service.metrics(importMetrics.filters(null, null, null, null));

        assertThat(r.target()).isEqualTo(85.0);
        assertThat(r.overall()).isEqualTo(new AdminAiMetricsResponse.Bank("TOTAL", 20, 5, 75.0, Status.YELLOW));
        assertThat(r.banks()).extracting(AdminAiMetricsResponse.Bank::bank).containsExactly("ITAU", "NUBANK");
        assertThat(r.banks().get(0).status()).isEqualTo(Status.RED);
        assertThat(r.months()).containsExactly("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");
        assertThat(r.trend()).containsExactly(
            new AdminAiMetricsResponse.Series("ITAU", Arrays.asList(null, 75.0, null, null, null, null)),
            new AdminAiMetricsResponse.Series("NUBANK", Arrays.asList(null, null, null, null, null, 90.0)));
        assertThat(r.monthChange()).isEqualTo(new AdminAiMetricsResponse.MonthChange(90.0, null, null));
        // The 6 months in São Paulo, whatever the period
        verify(history).countByBankAndMonth(OffsetDateTime.parse("2026-06-01T00:00-03:00"),
            OffsetDateTime.parse("2026-12-01T00:00-03:00"));
    }

    @Test
    void monthChangeIsTheDifferenceInPoints() {
        when(history.countByBankAndMonth(any(), any())).thenReturn(List.of(
            new Month("NUBANK", "2026-11", 10, 1), new Month("ITAU", "2026-11", 10, 1),
            new Month("NUBANK", "2026-10", 20, 3)));
        AdminAiMetricsResponse r = service.metrics(importMetrics.filters(null, null, null, null));
        assertThat(r.monthChange().current()).isEqualTo(90.0);
        assertThat(r.monthChange().previous()).isEqualTo(85.0);
        assertThat(r.monthChange().delta()).isEqualTo(5.0);
    }

    @Test
    void detailMergesTypesAndBandsAndSuggestsFrom20Classifications() {
        when(history.countByTypeAndBand(eq("NUBANK"), any(), any())).thenReturn(List.of(
            new Kind("PIX", "80-100", 15, 5), new Kind("PIX", "0-49", 5, 1), // PIX 20 at 70 %
            new Kind("CREDIT", "50-79", 19, 10))); // under 20: no suggestion
        when(patterns.findShared(3, 10)).thenReturn(List.of(new Shared("uber trip", 3, 7)));

        AdminAiBankDetailResponse d = service.detail(importMetrics.filters(null, null, "NUBANK", null));

        assertThat(d.types()).extracting(Group::key).containsExactly("CREDIT", "PIX");
        assertThat(d.types().get(1)).isEqualTo(new Group("PIX", 20, 6, 70.0, Status.RED));
        assertThat(d.confidence()).extracting(Group::key).containsExactly("0-49", "50-79", "80-100");
        assertThat(d.patterns()).containsExactly(new AdminAiBankDetailResponse.Pattern("uber trip", 3, 7));
        assertThat(d.suggestions()).containsExactly(
            "Revisar o prompt para Pix: precisão de 70,0 % em 20 classificações",
            "Incluir 'uber trip' como exemplo no prompt (3 usuários usam)");
    }

    @Test
    void withoutWeakGroupsOrPatternsThereIsNoSuggestion() {
        assertThat(AiMetricsService.suggestions(List.of(new Group("DEBIT", 100, 10, 90.0, Status.GREEN)),
            List.of(new Group("0-49", 20, 10, 50.0, Status.RED)), List.of()))
            .containsExactly("Revisar o prompt para confiança 0 a 49: precisão de 50,0 % em 20 classificações");
        assertThat(AiMetricsService.suggestions(List.of(), List.of(), List.of()))
            .containsExactly(AiMetricsService.NO_SUGGESTION);
    }

    @Test
    void detailNeedsABank() {
        assertThatThrownBy(() -> service.detail(importMetrics.filters(null, null, null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void csvHasOneLinePerBankAndTheTotal() {
        when(history.countByBank(any(), any(), isNull())).thenReturn(List.of(
            new Bank("NUBANK", 3, 1), new Bank("UNKNOWN", 1, 0)));
        assertThat(service.csv(importMetrics.filters(null, null, null, null)))
            .isEqualTo("﻿Banco;Classificações;Correções;Precisão (%)\r\n"
                + "Nubank;3;1;66,67\r\nNão identificado;1;0;100,00\r\nTotal;4;1;75,00\r\n");
    }
}
