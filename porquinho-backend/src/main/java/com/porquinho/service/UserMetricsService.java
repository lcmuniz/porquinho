package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.dto.AdminUserMetricsResponse;
import com.porquinho.dto.AdminUserMetricsResponse.Feature;
import com.porquinho.dto.AdminUserMetricsResponse.Goal;
import com.porquinho.repository.UserMetricsRepository;
import com.porquinho.repository.UserMetricsRepository.Adoption;
import com.porquinho.repository.UserMetricsRepository.MonthCounts;
import com.porquinho.repository.UserMetricsRepository.Summary;
import com.porquinho.repository.UserMetricsRepository.WeekCounts;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.porquinho.service.ImportMetricsService.SAO_PAULO;

/**
 * Story 10.4: active users, subscriptions, engagement and feature adoption for the admin panel, computed from the
 * existing tables on every call (no cache, no period). Days, weeks (Monday to Sunday) and months in São Paulo.
 */
@Service
public class UserMetricsService {

    static final double CHURN_TARGET = 5.0;
    static final double ONBOARDING_TARGET = 70.0;
    static final int POINTS = 12;
    static final int CHECKIN_WEEKS = 4;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /** Key and CSV label of each feature, in the order of the panel. */
    static final Map<String, String> FEATURE_LABELS = new LinkedHashMap<>();

    static {
        FEATURE_LABELS.put("ACCOUNTS", "Contas");
        FEATURE_LABELS.put("CREDIT_CARDS", "Cartões");
        FEATURE_LABELS.put("IMPORTS", "Importação concluída");
        FEATURE_LABELS.put("BUDGETS", "Orçamento");
        FEATURE_LABELS.put("GOALS", "Metas");
        FEATURE_LABELS.put("TRANSFERS", "Transferências");
        FEATURE_LABELS.put("RECONCILIATIONS", "Conciliação");
    }

    private static final Map<String, Function<Adoption, Long>> FEATURE_COUNTS = Map.of(
        "ACCOUNTS", Adoption::getAccounts,
        "CREDIT_CARDS", Adoption::getCreditCards,
        "IMPORTS", Adoption::getImports,
        "BUDGETS", Adoption::getBudgets,
        "GOALS", Adoption::getGoals,
        "TRANSFERS", Adoption::getTransfers,
        "RECONCILIATIONS", Adoption::getReconciliations);

    private final UserMetricsRepository repository;
    private final Clock clock;

    public UserMetricsService(UserMetricsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AdminUserMetricsResponse metrics() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ZonedDateTime local = now.atZoneSameInstant(SAO_PAULO);
        ZonedDateTime weekStart = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(SAO_PAULO);

        Summary s = repository.summary(now, weekStart.minusWeeks(CHECKIN_WEEKS).toOffsetDateTime(),
            weekStart.toOffsetDateTime());
        Double checkins = s.getActive30d() == 0 ? null
            : BigDecimal.valueOf(s.getCheckins())
                .divide(BigDecimal.valueOf((long) CHECKIN_WEEKS * s.getActive30d()), 2, RoundingMode.HALF_UP)
                .doubleValue();
        Double churn = percent(s.getCancelled(), s.getMonthly());
        Double onboarding = percent(s.getOnboarded(), s.getTotal());

        Adoption a = repository.adoption();
        List<Feature> adoption = FEATURE_LABELS.keySet().stream().map(key -> {
            long users = FEATURE_COUNTS.get(key).apply(a);
            return new Feature(key, users, percent(users, s.getTotal()));
        }).toList();

        YearMonth current = YearMonth.from(local);
        List<String> months = IntStream.range(0, POINTS)
            .mapToObj(i -> current.minusMonths(POINTS - 1 - i).toString()).toList();
        Map<String, MonthCounts> byMonth = repository.countByMonth(now).stream()
            .collect(Collectors.toMap(MonthCounts::getMonth, m -> m));
        List<Long> growth = months.stream().map(m -> byMonth.values().stream()
            .filter(c -> c.getMonth().compareTo(m) <= 0).mapToLong(MonthCounts::getUsers).sum()).toList();
        List<Double> conversionByMonth = months.stream().map(byMonth::get)
            .map(c -> c == null ? null : percent(c.getPaid(), c.getEligible())).toList();

        // The end of the oldest week; Brazil has no daylight saving time, so the next ends are 7 × 24 h apart
        ZonedDateTime oldest = weekStart.minusWeeks(POINTS - 1);
        List<String> weeks = IntStream.range(0, POINTS)
            .mapToObj(i -> oldest.plusWeeks(i).toLocalDate().toString()).toList();
        List<WeekCounts> active = repository.activeByWeek(oldest.plusWeeks(1).toOffsetDateTime(), now);

        return new AdminUserMetricsResponse(now, s.getTotal(), s.getActive24h(), s.getActive7d(), s.getActive30d(),
            s.getTrials(), s.getPaid(), new Goal(churn, CHURN_TARGET, churnStatus(churn)), checkins,
            new Goal(onboarding, ONBOARDING_TARGET, onboardingStatus(onboarding)),
            percent(s.getMonthly(), s.getMonthly() + s.getEndedTrials()), adoption, months, growth,
            conversionByMonth, weeks, active.stream().map(WeekCounts::getActive7d).toList(),
            active.stream().map(WeekCounts::getActive30d).toList());
    }

    public String fileName() {
        return "usuarios_" + LocalDate.now(clock.withZone(SAO_PAULO)) + ".csv";
    }

    /** One line per card, engagement metric and feature, pt-BR (Epic 9); an empty value is "sem dados". */
    public String csv() {
        return csv(metrics());
    }

    static String csv(AdminUserMetricsResponse m) {
        StringBuilder csv = new StringBuilder(CsvPtBr.BOM + "Métrica;Valor").append(CsvPtBr.CRLF);
        line(csv, "Total de usuários", String.valueOf(m.total()));
        line(csv, "Ativos em 24 h", String.valueOf(m.active24h()));
        line(csv, "Ativos em 7 dias", String.valueOf(m.active7d()));
        line(csv, "Ativos em 30 dias", String.valueOf(m.active30d()));
        line(csv, "Usuários em trial", String.valueOf(m.trials()));
        line(csv, "Usuários pagos", String.valueOf(m.paid()));
        line(csv, "Churn (%)", format(m.churn().rate(), 1));
        line(csv, "Check-ins semanais por usuário ativo", format(m.weeklyCheckins(), 2));
        line(csv, "Onboarding concluído (%)", format(m.onboarding().rate(), 1));
        line(csv, "Conversão trial para pago (%)", format(m.conversion(), 1));
        for (Feature f : m.adoption()) {
            line(csv, "Adoção: " + FEATURE_LABELS.get(f.feature()) + " (%)", format(f.rate(), 1));
        }
        return csv.toString();
    }

    /** Rounded once to 1 decimal (exact decimal, HALF_UP), so the color, the JSON and the CSV use the same value. */
    static Double percent(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP).doubleValue();
    }

    static Status churnStatus(Double churn) {
        if (churn == null) return Status.UNKNOWN;
        return churn <= CHURN_TARGET ? Status.GREEN : Status.RED;
    }

    static Status onboardingStatus(Double onboarding) {
        if (onboarding == null) return Status.UNKNOWN;
        return onboarding >= ONBOARDING_TARGET ? Status.GREEN : Status.YELLOW;
    }

    private static String format(Double value, int places) {
        return value == null ? "" : String.format(PT_BR, "%." + places + "f", value);
    }

    private static void line(StringBuilder csv, String label, String value) {
        csv.append(CsvPtBr.text(label)).append(';').append(value).append(CsvPtBr.CRLF);
    }
}
