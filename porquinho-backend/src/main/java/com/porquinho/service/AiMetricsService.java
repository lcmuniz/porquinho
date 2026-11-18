package com.porquinho.service;

import com.porquinho.dto.AdminAiBankDetailResponse;
import com.porquinho.dto.AdminAiBankDetailResponse.Group;
import com.porquinho.dto.AdminAiBankDetailResponse.Pattern;
import com.porquinho.dto.AdminAiMetricsResponse;
import com.porquinho.dto.AdminAiMetricsResponse.Bank;
import com.porquinho.dto.AdminAiMetricsResponse.MonthChange;
import com.porquinho.dto.AdminAiMetricsResponse.Series;
import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.AiClassificationHistoryRepository.BankCounts;
import com.porquinho.repository.AiClassificationHistoryRepository.KindCounts;
import com.porquinho.repository.AiClassificationHistoryRepository.MonthCounts;
import com.porquinho.repository.UserClassificationPatternRepository;
import com.porquinho.service.ImportMetricsService.Filters;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.porquinho.service.ImportMetricsService.BANK_LABELS;
import static com.porquinho.service.ImportMetricsService.SAO_PAULO;

/**
 * Story 10.3: AI classification accuracy per bank for the admin panel, aggregated from ai_classification_history. A
 * row counts once its transaction left the review and was not deleted; was_corrected is an error. The filters are
 * the ones of 10.2 ({@link ImportMetricsService#filters}), without the status.
 */
@Service
public class AiMetricsService {

    static final double TARGET = 85.0;
    static final double ATTENTION = 75.0;
    static final int TREND_MONTHS = 6;
    static final int MIN_SHARED_USERS = 3;
    static final int PATTERN_LIMIT = 10;
    static final int MIN_SUGGESTION_CLASSIFICATIONS = 20;
    static final String NO_SUGGESTION = "Nenhuma sugestão no período";

    static final Map<String, String> TYPE_LABELS = Map.of(
        "PIX", "Pix", "INSTALLMENT", "Parcelas", "DEBIT", "Débitos", "CREDIT", "Créditos");
    static final List<String> BANDS = List.of("0-49", "50-79", "80-100");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final AiClassificationHistoryRepository history;
    private final UserClassificationPatternRepository patterns;
    private final Clock clock;

    public AiMetricsService(AiClassificationHistoryRepository history, UserClassificationPatternRepository patterns,
                            Clock clock) {
        this.history = history;
        this.patterns = patterns;
        this.clock = clock;
    }

    public AdminAiMetricsResponse metrics(Filters f) {
        List<Bank> banks = banks(f);
        long total = banks.stream().mapToLong(Bank::classifications).sum();
        long corrections = banks.stream().mapToLong(Bank::corrections).sum();
        Bank overall = bank("TOTAL", total, corrections);

        // The last 6 calendar months (São Paulo) up to the current one, whatever the period
        YearMonth current = YearMonth.now(clock.withZone(SAO_PAULO));
        List<String> months = IntStream.range(0, TREND_MONTHS)
            .mapToObj(i -> current.minusMonths(TREND_MONTHS - 1 - i).toString())
            .toList();
        List<MonthCounts> rows = history.countByBankAndMonth(start(current.minusMonths(TREND_MONTHS - 1).atDay(1)),
            start(current.plusMonths(1).atDay(1)));

        Map<String, Map<String, long[]>> byBank = new TreeMap<>();
        Map<String, long[]> overallByMonth = new LinkedHashMap<>();
        for (MonthCounts r : rows) {
            byBank.computeIfAbsent(r.getBank(), b -> new LinkedHashMap<>())
                .put(r.getMonth(), new long[] {r.getClassifications(), r.getCorrections()});
            long[] sum = overallByMonth.computeIfAbsent(r.getMonth(), m -> new long[2]);
            sum[0] += r.getClassifications();
            sum[1] += r.getCorrections();
        }
        List<Series> trend = byBank.entrySet().stream()
            .map(e -> new Series(e.getKey(), months.stream().map(m -> accuracy(e.getValue().get(m))).toList()))
            .toList();

        Double now = accuracy(overallByMonth.get(current.toString()));
        Double before = accuracy(overallByMonth.get(current.minusMonths(1).toString()));
        MonthChange change = new MonthChange(now, before, now == null || before == null ? null : now - before);

        return new AdminAiMetricsResponse(f.from(), f.to(), TARGET, overall, change, banks, months, trend);
    }

    /** One bank (required here, optional in the route so the 403 comes before the 400). */
    public AdminAiBankDetailResponse detail(Filters f) {
        if (f.bank() == null) throw new BadRequestException(ImportMetricsService.INVALID_BANK, "bank");
        Map<String, long[]> byType = new LinkedHashMap<>();
        Map<String, long[]> byBand = new TreeMap<>(Comparator.comparingInt(BANDS::indexOf));
        for (KindCounts k : history.countByTypeAndBand(f.bank(), start(f.from()), start(f.to().plusDays(1)))) {
            add(byType, k.getType(), k);
            add(byBand, k.getBand(), k);
        }
        List<Group> types = byType.entrySet().stream().map(e -> group(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(Group::accuracy, Comparator.nullsLast(Comparator.<Double>naturalOrder()))
                .thenComparing(Group::key))
            .toList();
        List<Group> confidence = byBand.entrySet().stream().map(e -> group(e.getKey(), e.getValue())).toList();
        List<Pattern> shared = patterns.findShared(MIN_SHARED_USERS, PATTERN_LIMIT).stream()
            .map(p -> new Pattern(p.getPattern(), p.getUsers(), p.getUsage()))
            .toList();
        return new AdminAiBankDetailResponse(f.bank(), f.from(), f.to(), types, confidence, shared,
            suggestions(types, confidence, shared));
    }

    /** One line per bank (table order) and a final Total, pt-BR (Epic 9). */
    public String csv(Filters f) {
        StringBuilder csv = new StringBuilder(CsvPtBr.BOM + "Banco;Classificações;Correções;Precisão (%)")
            .append(CsvPtBr.CRLF);
        List<Bank> banks = banks(f);
        for (Bank b : banks) {
            line(csv, BANK_LABELS.getOrDefault(b.bank(), b.bank()), b);
        }
        line(csv, "Total", bank("TOTAL", banks.stream().mapToLong(Bank::classifications).sum(),
            banks.stream().mapToLong(Bank::corrections).sum()));
        return csv.toString();
    }

    /** A type or band below the target with at least 20 classifications, then each shared pattern. */
    static List<String> suggestions(List<Group> types, List<Group> confidence, List<Pattern> shared) {
        List<String> out = new ArrayList<>();
        Predicate<Group> weak = g -> g.accuracy() != null && g.accuracy() < TARGET
            && g.classifications() >= MIN_SUGGESTION_CLASSIFICATIONS;
        types.stream().filter(weak).forEach(g -> out.add(prompt(TYPE_LABELS.getOrDefault(g.key(), g.key()), g)));
        confidence.stream().filter(weak)
            .forEach(g -> out.add(prompt("confiança " + g.key().replace("-", " a "), g)));
        shared.forEach(p -> out.add("Incluir '" + p.pattern() + "' como exemplo no prompt (" + p.users()
            + " usuários usam)"));
        if (out.isEmpty()) out.add(NO_SUGGESTION);
        return out;
    }

    /** Rounded once to 1 decimal, so the color, the JSON, the CSV and the texts use the same value. */
    static Double accuracy(long classifications, long corrections) {
        if (classifications == 0) return null;
        return Math.round((1 - (double) corrections / classifications) * 1000) / 10.0;
    }

    static Status status(Double accuracy) {
        if (accuracy == null) return Status.UNKNOWN;
        if (accuracy >= TARGET) return Status.GREEN;
        return accuracy >= ATTENTION ? Status.YELLOW : Status.RED;
    }

    private List<Bank> banks(Filters f) {
        return history.countByBank(start(f.from()), start(f.to().plusDays(1)), f.bank()).stream()
            .map((BankCounts c) -> bank(c.getBank(), c.getClassifications(), c.getCorrections()))
            .sorted(Comparator.comparing(Bank::accuracy, Comparator.nullsLast(Comparator.<Double>naturalOrder()))
                .thenComparing(Bank::bank))
            .toList();
    }

    private static Bank bank(String bank, long classifications, long corrections) {
        Double accuracy = accuracy(classifications, corrections);
        return new Bank(bank, classifications, corrections, accuracy, status(accuracy));
    }

    private static Group group(String key, long[] c) {
        Double accuracy = accuracy(c[0], c[1]);
        return new Group(key, c[0], c[1], accuracy, status(accuracy));
    }

    private static Double accuracy(long[] c) {
        return c == null ? null : accuracy(c[0], c[1]);
    }

    private static void add(Map<String, long[]> map, String key, KindCounts k) {
        long[] c = map.computeIfAbsent(key, x -> new long[2]);
        c[0] += k.getClassifications();
        c[1] += k.getCorrections();
    }

    private static String prompt(String label, Group g) {
        return String.format(PT_BR, "Revisar o prompt para %s: precisão de %.1f %% em %d classificações", label,
            g.accuracy(), g.classifications());
    }

    private static void line(StringBuilder csv, String label, Bank b) {
        csv.append(String.join(";", CsvPtBr.text(label), String.valueOf(b.classifications()),
                String.valueOf(b.corrections()),
                CsvPtBr.money(b.accuracy() == null ? null : BigDecimal.valueOf(b.accuracy()))))
            .append(CsvPtBr.CRLF);
    }

    private static OffsetDateTime start(LocalDate day) {
        return day.atStartOfDay(SAO_PAULO).toOffsetDateTime();
    }
}
