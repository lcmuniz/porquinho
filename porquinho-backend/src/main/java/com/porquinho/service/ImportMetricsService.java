package com.porquinho.service;

import com.porquinho.dto.AdminHealthResponse.Status;
import com.porquinho.dto.AdminImportBankDetailResponse;
import com.porquinho.dto.AdminImportBankDetailResponse.DayPoint;
import com.porquinho.dto.AdminImportBankDetailResponse.ErrorTypeCount;
import com.porquinho.dto.AdminImportBankDetailResponse.Item;
import com.porquinho.dto.AdminImportMetricsResponse;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionImportRepository.DayCounts;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import static com.porquinho.service.AdminMetrics.start;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Story 10.2: import error rate per bank for the admin panel, aggregated from transaction_imports. FAILED is a
 * failure; REVIEW, COMPLETED and DISCARDED are successes (the file was read); PROCESSING is not counted yet.
 */
@Service
public class ImportMetricsService {

    static final double TARGET = 5.0;
    /** Limit of the 10.5 alert (the panel is red above TARGET). */
    static final double ALERT_RATE = 10.0;
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final int TREND_DAYS = 30;
    static final int RECENT_LIMIT = 20;
    static final String UNKNOWN = "UNKNOWN";
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    static final String INVALID_PERIOD = "Período inválido: use datas AAAA-MM-DD, início até o fim e no máximo 366 dias.";
    static final String INVALID_BANK = "Banco inválido.";
    static final String INVALID_STATUS = "Status inválido: use ALL, FAILED ou SUCCESS.";

    static final Map<String, String> BANK_LABELS = Map.of(
        OfxBankDetector.NUBANK, "Nubank",
        OfxBankDetector.BANCO_DO_BRASIL, "Banco do Brasil",
        OfxBankDetector.ITAU, "Itaú",
        OfxBankDetector.BRADESCO, "Bradesco",
        OfxBankDetector.CAIXA, "Caixa",
        OfxBankDetector.OTHER, "Outro banco",
        UNKNOWN, "Não identificado");

    static final Map<TransactionImport.Status, String> STATUS_LABELS = Map.of(
        TransactionImport.Status.PROCESSING, "Em processamento",
        TransactionImport.Status.REVIEW, "Aguardando revisão",
        TransactionImport.Status.COMPLETED, "Concluída",
        TransactionImport.Status.FAILED, "Falhou",
        TransactionImport.Status.DISCARDED, "Descartada");

    private static final Set<String> OUTCOMES = Set.of("ALL", "FAILED", "SUCCESS");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Validated filters: [from, to] in America/Sao_Paulo days, bank null = every bank, outcome ALL/FAILED/SUCCESS. */
    public record Filters(LocalDate from, LocalDate to, String bank, String outcome) {
    }

    private final TransactionImportRepository repository;
    private final Clock clock;

    public ImportMetricsService(TransactionImportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Every parameter is optional: the default period is the last 30 days up to today. 400 on any invalid value. */
    public Filters filters(String from, String to, String bank, String status) {
        LocalDate end = isBlank(to) ? today() : date(to, "to");
        LocalDate start = isBlank(from) ? end.minusDays(DEFAULT_DAYS - 1) : date(from, "from");
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BadRequestException(INVALID_PERIOD, "from");
        }
        String b = isBlank(bank) ? null : bank.trim();
        if (b != null && !BANK_LABELS.containsKey(b)) throw new BadRequestException(INVALID_BANK, "bank");
        String outcome = isBlank(status) ? "ALL" : status.trim();
        if (!OUTCOMES.contains(outcome)) throw new BadRequestException(INVALID_STATUS, "status");
        return new Filters(start, end, b, outcome);
    }

    public AdminImportMetricsResponse metrics(Filters f) {
        List<AdminImportMetricsResponse.Bank> banks = repository.countByBank(start(f.from()), start(f.to().plusDays(1)), f.bank())
            .stream()
            .map(c -> {
                Double rate = rate(c.getFailures(), c.getSuccesses());
                return new AdminImportMetricsResponse.Bank(c.getBank(), c.getSuccesses() + c.getFailures(),
                    c.getSuccesses(), c.getFailures(), rate, status(rate));
            })
            .sorted(Comparator.comparing(AdminImportMetricsResponse.Bank::errorRate,
                    Comparator.nullsLast(Comparator.<Double>reverseOrder()))
                .thenComparing(AdminImportMetricsResponse.Bank::bank))
            .toList();
        return new AdminImportMetricsResponse(f.from(), f.to(), TARGET, banks);
    }

    /** One bank (required here, optional in the route so the 403 comes before the 400). */
    public AdminImportBankDetailResponse detail(Filters f) {
        if (f.bank() == null) throw new BadRequestException(INVALID_BANK, "bank");
        OffsetDateTime from = start(f.from());
        OffsetDateTime to = start(f.to().plusDays(1));

        Map<ImportErrorType, Long> byType = new EnumMap<>(ImportErrorType.class);
        repository.countFailuresByMessage(f.bank(), from, to)
            .forEach(m -> byType.merge(ImportErrorType.of(m.getMessage()), m.getCount(), Long::sum));
        List<ErrorTypeCount> errorTypes = byType.entrySet().stream()
            .map(e -> new ErrorTypeCount(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(ErrorTypeCount::count).reversed().thenComparing(ErrorTypeCount::type))
            .toList();

        List<Item> recent = repository.findFinished(from, to, f.bank(), f.outcome(), RECENT_LIMIT).stream()
            .map(ImportMetricsService::item)
            .toList();

        return new AdminImportBankDetailResponse(f.bank(), f.from(), f.to(), errorTypes, recent, trend(f.bank()));
    }

    /** One line per finished import that passes the filters, pt-BR (Epic 9). No user, file name or counters. */
    public String csv(Filters f) {
        StringBuilder csv = new StringBuilder(CsvPtBr.BOM + "Data;Banco;Tipo de arquivo;Status;Tipo de erro;Mensagem")
            .append(CsvPtBr.CRLF);
        for (TransactionImport i : repository.findFinished(start(f.from()), start(f.to().plusDays(1)), f.bank(),
                f.outcome(), Integer.MAX_VALUE)) {
            Item item = item(i);
            csv.append(String.join(";",
                    item.createdAt().atZoneSameInstant(SAO_PAULO).format(CSV_DATE),
                    CsvPtBr.text(BANK_LABELS.getOrDefault(item.bank(), item.bank())),
                    item.fileType().name(),
                    CsvPtBr.text(STATUS_LABELS.get(item.status())),
                    item.errorType() == null ? "" : CsvPtBr.text(item.errorType().label()),
                    CsvPtBr.text(item.message())))
                .append(CsvPtBr.CRLF);
        }
        return csv.toString();
    }

    /** The last 30 days up to today, every day present; null rate on a day without a finished import. */
    List<DayPoint> trend(String bank) {
        LocalDate today = today();
        LocalDate first = today.minusDays(TREND_DAYS - 1);
        Map<LocalDate, DayCounts> byDay = repository.countByDay(bank, start(first), start(today.plusDays(1))).stream()
            .collect(Collectors.toMap(d -> LocalDate.parse(d.getDay()), Function.identity()));
        return first.datesUntil(today.plusDays(1))
            .map(day -> {
                DayCounts c = byDay.get(day);
                long failures = c == null ? 0 : c.getFailures();
                long successes = c == null ? 0 : c.getSuccesses();
                return new DayPoint(day, failures + successes, failures, rate(failures, successes));
            })
            .toList();
    }

    /** Rounded once to 1 decimal ({@link AdminMetrics}), so the color, the JSON, the chart and the alert agree. */
    static Double rate(long failures, long successes) {
        return AdminMetrics.percent(failures, failures + successes);
    }

    static Status status(Double rate) {
        if (rate == null) return Status.UNKNOWN;
        return rate > TARGET ? Status.RED : Status.GREEN;
    }

    private static Item item(TransactionImport i) {
        boolean failed = i.getStatus() == TransactionImport.Status.FAILED;
        return new Item(i.getCreatedAt(), i.getBankName() == null ? UNKNOWN : i.getBankName(), i.getFileType(),
            i.getStatus(), failed ? ImportErrorType.of(i.getErrorMessage()) : null, i.getErrorMessage());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SAO_PAULO));
    }

    private static LocalDate date(String value, String field) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_PERIOD, field);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
