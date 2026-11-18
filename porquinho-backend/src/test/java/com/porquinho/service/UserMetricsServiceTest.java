package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.dto.AdminUserMetricsResponse;
import com.porquinho.repository.UserMetricsRepository;
import com.porquinho.repository.UserMetricsRepository.Adoption;
import com.porquinho.repository.UserMetricsRepository.MonthCounts;
import com.porquinho.repository.UserMetricsRepository.Summary;
import com.porquinho.repository.UserMetricsRepository.WeekCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMetricsServiceTest {

    // 01:00 UTC on Tuesday, December 1st is still Monday, November 30th in America/Sao_Paulo (UTC-3)
    private static final Instant NOW = Instant.parse("2026-12-01T01:00:00Z");

    private UserMetricsRepository repository;
    private UserMetricsService service;

    record S(long getTotal, long getActive24h, long getActive7d, long getActive30d, long getTrials, long getPaid,
             long getMonthly, long getCancelled, long getEndedTrials, long getOnboarded, long getCheckins)
        implements Summary {
    }

    record A(long getAccounts, long getCreditCards, long getImports, long getBudgets, long getGoals,
             long getTransfers, long getReconciliations) implements Adoption {
    }

    record M(String getMonth, long getUsers, long getPaid, long getEligible) implements MonthCounts {
    }

    record W(int getWeek, long getActive7d, long getActive30d) implements WeekCounts {
    }

    @BeforeEach
    void setUp() {
        repository = mock(UserMetricsRepository.class);
        service = new UserMetricsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.adoption()).thenReturn(new A(10, 9, 8, 7, 6, 5, 4));
        when(repository.countByMonth(any())).thenReturn(List.<MonthCounts>of(
            new M("2025-01", 4, 0, 0), new M("2026-11", 3, 1, 3), new M("2026-12", 3, 0, 0)));
        when(repository.activeByWeek(any(), any())).thenReturn(IntStream.range(0, 12)
            .mapToObj(i -> (WeekCounts) new W(i, i, 2L * i)).toList());
    }

    private void summary(long total, long active30d, long monthly, long cancelled, long endedTrials, long onboarded,
                         long checkins) {
        when(repository.summary(any(), any(), any())).thenReturn(
            new S(total, 1, 2, active30d, 4, monthly - cancelled, monthly, cancelled, endedTrials, onboarded, checkins));
    }

    @Test
    void churnUpToFivePercentIsGreenAndAboveIsRed() {
        summary(10, 10, 1000, 50, 0, 7, 0);
        assertThat(service.metrics().churn()).extracting(g -> g.rate(), g -> g.status()).containsExactly(5.0, Status.GREEN);
        summary(10, 10, 1000, 51, 0, 7, 0);
        assertThat(service.metrics().churn()).extracting(g -> g.rate(), g -> g.status()).containsExactly(5.1, Status.RED);
        // 4.96 % rounds to 5.0 before the color
        summary(10, 10, 10000, 496, 0, 7, 0);
        assertThat(service.metrics().churn().status()).isEqualTo(Status.GREEN);
    }

    @Test
    void onboardingFromSeventyIsGreenAndBelowIsYellow() {
        summary(10, 10, 1, 0, 0, 7, 0);
        assertThat(service.metrics().onboarding()).extracting(g -> g.rate(), g -> g.target(), g -> g.status())
            .containsExactly(70.0, 70.0, Status.GREEN);
        summary(1000, 10, 1, 0, 0, 699, 0);
        assertThat(service.metrics().onboarding()).extracting(g -> g.rate(), g -> g.status())
            .containsExactly(69.9, Status.YELLOW);
    }

    @Test
    void noDenominatorGivesNullAndUnknown() {
        when(repository.adoption()).thenReturn(new A(0, 0, 0, 0, 0, 0, 0));
        when(repository.countByMonth(any())).thenReturn(List.of());
        summary(0, 0, 0, 0, 0, 0, 0);

        AdminUserMetricsResponse m = service.metrics();

        assertThat(m.churn().rate()).isNull();
        assertThat(m.churn().status()).isEqualTo(Status.UNKNOWN);
        assertThat(m.onboarding().rate()).isNull();
        assertThat(m.onboarding().status()).isEqualTo(Status.UNKNOWN);
        assertThat(m.weeklyCheckins()).isNull();
        assertThat(m.conversion()).isNull();
        assertThat(m.adoption()).allSatisfy(f -> assertThat(f.rate()).isNull());
        assertThat(m.conversionByMonth()).containsOnlyNulls().hasSize(12);
        assertThat(m.growth()).containsOnly(0L);
    }

    @Test
    void conversionCheckinsAndSeriesOfTwelvePoints() {
        summary(10, 4, 3, 1, 1, 7, 6);

        AdminUserMetricsResponse m = service.metrics();

        assertThat(m.conversion()).isEqualTo(75.0); // 3 / (3 + 1)
        assertThat(m.weeklyCheckins()).isEqualTo(0.38); // 6 / 4 / 4 = 0.375, HALF_UP
        assertThat(m.adoption()).extracting(f -> f.feature()).containsExactly("ACCOUNTS", "CREDIT_CARDS", "IMPORTS",
            "BUDGETS", "GOALS", "TRANSFERS", "RECONCILIATIONS");
        // A distinct count per feature: each key reads its own column
        assertThat(m.adoption()).extracting(f -> f.users()).containsExactly(10L, 9L, 8L, 7L, 6L, 5L, 4L);
        assertThat(m.adoption()).extracting(f -> f.rate()).containsExactly(100.0, 90.0, 80.0, 70.0, 60.0, 50.0, 40.0);

        // The current São Paulo month is still November
        assertThat(m.months()).hasSize(12).startsWith("2025-12").endsWith("2026-11");
        assertThat(m.growth()).startsWith(4L).endsWith(7L); // the December user is not in November yet
        assertThat(m.conversionByMonth().get(11)).isEqualTo(33.3);
        assertThat(m.conversionByMonth().get(10)).isNull();

        // The current São Paulo week started on Monday, November 30th
        assertThat(m.weeks()).hasSize(12).startsWith("2026-09-14").endsWith("2026-11-30");
        assertThat(m.activeWeekly()).hasSize(12).endsWith(11L);
        assertThat(m.activeMonthly()).endsWith(22L);
        verify(repository).activeByWeek(OffsetDateTime.parse("2026-09-21T00:00-03:00"),
            OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(repository).summary(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
            OffsetDateTime.parse("2026-11-02T00:00-03:00"), OffsetDateTime.parse("2026-11-30T00:00-03:00"));
    }

    @Test
    void csvHasOneLinePerMetricWithOneDecimalAndEmptyWithoutData() {
        summary(10, 4, 0, 0, 1, 7, 6);

        String csv = service.csv();

        assertThat(csv).isEqualTo("﻿Métrica;Valor\r\n"
            + "Total de usuários;10\r\n"
            + "Ativos em 24 h;1\r\n"
            + "Ativos em 7 dias;2\r\n"
            + "Ativos em 30 dias;4\r\n"
            + "Usuários em trial;4\r\n"
            + "Usuários pagos;0\r\n"
            + "Churn (%);\r\n"
            + "Check-ins semanais por usuário ativo;0,38\r\n"
            + "Onboarding concluído (%);70,0\r\n"
            + "Conversão trial para pago (%);0,0\r\n"
            + "Adoção: Contas (%);100,0\r\n"
            + "Adoção: Cartões (%);90,0\r\n"
            + "Adoção: Importação concluída (%);80,0\r\n"
            + "Adoção: Orçamento (%);70,0\r\n"
            + "Adoção: Metas (%);60,0\r\n"
            + "Adoção: Transferências (%);50,0\r\n"
            + "Adoção: Conciliação (%);40,0\r\n");
        assertThat(service.fileName()).isEqualTo("usuarios_2026-11-30.csv");
        assertThat(Collections.frequency(List.of(csv.split("\r\n")), "")).isZero();
    }
}
