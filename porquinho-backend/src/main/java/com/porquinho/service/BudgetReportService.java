package com.porquinho.service;

import com.porquinho.entity.BudgetAllocation;
import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.BudgetAllocationRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Story 9.2: budget report per child category as CSV for Excel pt-BR (BOM, ";", decimal comma, CRLF). Spent follows
 * {@link TransactionRepository#sumSpentByCategoryAndMonth}; over a range, allocated and spent are summed and the status
 * comes from {@link BudgetService#status} on the sums. Averages use the 3 and 6 months before {@code from}.
 */
@Service
public class BudgetReportService {

    public static final String INVALID_RANGE_MESSAGE =
        "Período inválido: use meses AAAA-MM, com o início até o fim e no máximo 12 meses";
    static final int MAX_MONTHS = 12;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN_PERCENT_UP = new BigDecimal("1.1");
    private static final BigDecimal TEN_PERCENT_DOWN = new BigDecimal("0.9");

    private final CategoryRepository categoryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final TransactionRepository transactionRepository;

    public BudgetReportService(CategoryRepository categoryRepository,
                               BudgetAllocationRepository budgetAllocationRepository,
                               TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * One row's figures: allocated is null without any allocation; win3/win6 are the spent of the windows; unbudgeted
     * is the spent of the children without allocation, only on a group row with some allocated child.
     */
    private record Line(String name, String group, BigDecimal allocated, BigDecimal spent, BigDecimal win3,
                        BigDecimal win6, BigDecimal unbudgeted) {}

    /** Throws 400 on a malformed month, {@code from > to} or more than 12 months. */
    @Transactional(readOnly = true)
    public String csv(UUID userId, String fromParam, String toParam, boolean hierarchy, boolean averages) {
        YearMonth from = parse(fromParam);
        YearMonth to = parse(toParam);
        if (from.isAfter(to) || from.plusMonths(MAX_MONTHS - 1).isBefore(to)) {
            throw new BadRequestException(INVALID_RANGE_MESSAGE, "from");
        }

        List<Category> categories = categoryRepository.findActiveByUserId(userId);
        Map<UUID, String> groups = new HashMap<>();
        categories.stream().filter(c -> c.getParentCategoryId() == null)
            .forEach(c -> groups.put(c.getId(), c.getCategoryName()));
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        List<Category> children = categories.stream().filter(c -> c.getParentCategoryId() != null)
            .sorted(Comparator.comparing((Category c) -> groups.getOrDefault(c.getParentCategoryId(), ""), collator)
                .thenComparing(Category::getCategoryName, collator))
            .toList();

        Map<UUID, BigDecimal> allocated = new HashMap<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            for (BudgetAllocation a : budgetAllocationRepository.findByUserIdAndMonthYear(userId, m.atDay(1))) {
                allocated.merge(a.getCategoryId(), a.getAllocatedAmount(), BigDecimal::add);
            }
        }

        Map<UUID, BigDecimal> spent = new HashMap<>();
        Map<UUID, BigDecimal> win3 = new HashMap<>();
        Map<UUID, BigDecimal> win6 = new HashMap<>();
        for (Object[] r : transactionRepository.sumSpentByCategoryAndMonth(userId,
                (averages ? from.minusMonths(6) : from).atDay(1),
                to.atEndOfMonth())) {
            if (r[0] == null) continue;
            UUID id = (UUID) r[0];
            YearMonth month = YearMonth.of(((Number) r[1]).intValue(), ((Number) r[2]).intValue());
            BigDecimal sum = (BigDecimal) r[3];
            if (!month.isBefore(from)) {
                spent.merge(id, sum, BigDecimal::add);
            } else {
                win6.merge(id, sum, BigDecimal::add);
                if (!month.isBefore(from.minusMonths(3))) win3.merge(id, sum, BigDecimal::add);
            }
        }

        // children keep their order; a group's total goes right before its first child
        Map<UUID, List<Line>> byGroup = new LinkedHashMap<>();
        for (Category c : children) {
            byGroup.computeIfAbsent(c.getParentCategoryId(), k -> new ArrayList<>()).add(new Line(c.getCategoryName(),
                groups.getOrDefault(c.getParentCategoryId(), ""), allocated.get(c.getId()),
                spent.getOrDefault(c.getId(), BigDecimal.ZERO), win3.getOrDefault(c.getId(), BigDecimal.ZERO),
                win6.getOrDefault(c.getId(), BigDecimal.ZERO), null));
        }

        StringBuilder csv = new StringBuilder(CsvPtBr.BOM + "Categoria;Grupo;Alocado;Gasto;Restante;% usado;Status");
        if (hierarchy) csv.append(";Gasto sem orçamento");
        if (averages) csv.append(";Média 3 meses;Média 6 meses;Tendência");
        csv.append(CsvPtBr.CRLF);
        for (Map.Entry<UUID, List<Line>> entry : byGroup.entrySet()) {
            if (hierarchy) append(csv, groupLine(groups.getOrDefault(entry.getKey(), ""), entry.getValue()), true, averages);
            for (Line l : entry.getValue()) append(csv, l, hierarchy, averages);
        }
        return csv.toString();
    }

    /**
     * The 6.9 rule (sumRows in BudgetComparisonView): with some allocated child, allocated and spent use only those and
     * the others' spent goes to unbudgeted; with none, spent sums every child. Averages always sum every child.
     */
    private static Line groupLine(String name, List<Line> children) {
        BigDecimal allocated = null;
        BigDecimal budgetedSpent = BigDecimal.ZERO;
        BigDecimal allSpent = BigDecimal.ZERO;
        BigDecimal win3 = BigDecimal.ZERO;
        BigDecimal win6 = BigDecimal.ZERO;
        for (Line l : children) {
            if (l.allocated() != null) {
                allocated = allocated == null ? l.allocated() : allocated.add(l.allocated());
                budgetedSpent = budgetedSpent.add(l.spent());
            }
            allSpent = allSpent.add(l.spent());
            win3 = win3.add(l.win3());
            win6 = win6.add(l.win6());
        }
        return allocated == null ? new Line(name, "", null, allSpent, win3, win6, null)
            : new Line(name, "", allocated, budgetedSpent, win3, win6, allSpent.subtract(budgetedSpent));
    }

    private static void append(StringBuilder csv, Line l, boolean hierarchy, boolean averages) {
        BigDecimal remaining = l.allocated() == null ? null : l.allocated().subtract(l.spent());
        String percent = l.allocated() == null || l.allocated().signum() == 0 ? ""
            : l.spent().multiply(HUNDRED).divide(l.allocated(), 0, RoundingMode.HALF_UP).toPlainString();
        csv.append(String.join(";",
            CsvPtBr.text(l.name()),
            CsvPtBr.text(l.group()),
            CsvPtBr.money(l.allocated()),
            CsvPtBr.money(l.spent()),
            CsvPtBr.money(remaining),
            percent,
            statusLabel(BudgetService.status(l.allocated(), remaining))));
        if (hierarchy) csv.append(';').append(CsvPtBr.money(l.unbudgeted()));
        if (averages) {
            csv.append(';').append(CsvPtBr.money(l.win3().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)))
                .append(';').append(CsvPtBr.money(l.win6().divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP)))
                .append(';').append(trend(l.win3(), l.win6()));
        }
        csv.append(CsvPtBr.CRLF);
    }

    /** avg3 / avg6 = 2 × win3 / win6, compared without rounding: ≥ +10% "Subindo", ≤ −10% "Caindo". */
    static String trend(BigDecimal win3, BigDecimal win6) {
        BigDecimal twice3 = win3.multiply(BigDecimal.valueOf(2));
        if (win6.signum() == 0) return win3.signum() == 0 ? "Estável" : "Subindo";
        if (twice3.compareTo(win6.multiply(TEN_PERCENT_UP)) >= 0) return "Subindo";
        if (twice3.compareTo(win6.multiply(TEN_PERCENT_DOWN)) <= 0) return "Caindo";
        return "Estável";
    }

    private static String statusLabel(com.porquinho.dto.BudgetStatus status) {
        if (status == null) return "";
        return switch (status) {
            case HEALTHY -> "Saudável";
            case LOW -> "Saldo baixo";
            case OVERSPENT -> "Estourada";
        };
    }

    private static YearMonth parse(String month) {
        if (month == null) throw new BadRequestException(INVALID_RANGE_MESSAGE, "from");
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_RANGE_MESSAGE, "from");
        }
    }
}
