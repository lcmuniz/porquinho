package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.DashboardLayer2Response;
import com.porquinho.dto.DashboardLayer3Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.dto.TransactionResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Goal;
import com.porquinho.entity.Transaction;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.TransactionRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** Dashboard GPS (Epic 7). Story 7.2: the financial health traffic light. Story 7.3: the 5 essential metrics.
 * Story 7.4: the wealth curve inside the layer 1. Story 7.5: the layer 2 (spending per category). Story 7.6: the layer 3
 * (transaction list with filters and its CSV). */
@Service
public class DashboardService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final int DETAILS_PAGE_SIZE = 50;
    static final String INVALID_PERIOD_MESSAGE = "Período inválido: use datas AAAA-MM-DD com o início até o fim";
    static final String INVALID_SORT_MESSAGE = "Ordenação inválida";
    static final String INVALID_PAGE_MESSAGE = "Página inválida";
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BudgetService budgetService;
    private final AccountService accountService;
    private final GoalService goalService;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceHistoryRepository balanceHistoryRepository;
    private final CreditCardRepository creditCardRepository;
    private final TransactionService transactionService;
    private final DashboardCache dashboardCache;
    private final Clock clock;

    public DashboardService(BudgetService budgetService, AccountService accountService, GoalService goalService,
                            CategoryService categoryService, TransactionRepository transactionRepository,
                            AccountRepository accountRepository,
                            AccountBalanceHistoryRepository balanceHistoryRepository,
                            CreditCardRepository creditCardRepository, TransactionService transactionService,
                            DashboardCache dashboardCache, Clock clock) {
        this.dashboardCache = dashboardCache;
        this.creditCardRepository = creditCardRepository;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.accountService = accountService;
        this.goalService = goalService;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.clock = clock;
    }

    /**
     * Critical: under 50% healthy, 2+ overspent or a negative account. Attention: under 80% healthy, 1 overspent or a
     * goal behind. Healthy otherwise. Without any budgeted category the category criteria are skipped.
     */
    @Transactional(readOnly = true)
    public FinancialHealthResponse health(UUID userId) {
        return dashboardCache.get(DashboardCache.Layer.HEALTH, userId, null, FinancialHealthResponse.class,
            () -> computeHealth(userId));
    }

    private FinancialHealthResponse computeHealth(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        BudgetResponse budget = budgetService.get(userId, YearMonth.from(today).toString());
        int healthy = 0, low = 0, overspent = 0;
        for (BudgetResponse.CategoryBudget c : budget.categories()) {
            if (c.status() == BudgetStatus.HEALTHY) healthy++;
            else if (c.status() == BudgetStatus.LOW) low++;
            else if (c.status() == BudgetStatus.OVERSPENT) overspent++;
        }
        int negativeAccounts = (int) accountService.listActive(userId).stream()
            .filter(a -> a.getCurrentBalance().signum() < 0).count();
        int goalsBehind = (int) goalService.listActive(userId).stream()
            .filter(g -> !g.isCompleted() && isBehind(g, today)).count();

        int budgeted = healthy + low + overspent;
        // healthy / budgeted < x  <=>  healthy * 100 < x * budgeted (integers, no rounding)
        boolean under50 = budgeted > 0 && healthy * 100 < 50 * budgeted;
        boolean under80 = budgeted > 0 && healthy * 100 < 80 * budgeted;
        HealthStatus status;
        if (under50 || overspent >= 2 || negativeAccounts > 0) status = HealthStatus.CRITICAL;
        else if (under80 || overspent == 1 || goalsBehind > 0) status = HealthStatus.ATTENTION;
        else status = HealthStatus.HEALTHY;
        return new FinancialHealthResponse(status, healthy, low, overspent, negativeAccounts, goalsBehind);
    }

    /**
     * Story 7.3. Projection (real money, with or without category): total available - (spent up to today / today's
     * day x days left) - debits dated after today in the month - the month's future installments. The spent card
     * keeps the budget screen's number (whole month, categories only). The main goal is the oldest active one not completed.
     */
    @Transactional(readOnly = true)
    public DashboardLayer1Response overview(UUID userId) {
        return dashboardCache.get(DashboardCache.Layer.LAYER1, userId, null, DashboardLayer1Response.class,
            () -> computeOverview(userId));
    }

    private DashboardLayer1Response computeOverview(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        BudgetResponse budget = budgetService.get(userId, YearMonth.from(today).toString());
        Map<UUID, String> names = categoryService.listActive(userId).stream()
            .collect(Collectors.toMap(Category::getId, Category::getCategoryName));
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        // BudgetStatus is HEALTHY, LOW, OVERSPENT: reversed puts the overspent first
        List<DashboardLayer1Response.CategoryStatus> categories = budget.categories().stream()
            .filter(c -> c.status() != null && names.containsKey(c.categoryId()))
            .map(c -> new DashboardLayer1Response.CategoryStatus(c.categoryId(), names.get(c.categoryId()), c.status(),
                c.remaining()))
            .sorted(Comparator.comparing(DashboardLayer1Response.CategoryStatus::status, Comparator.reverseOrder())
                .thenComparing(DashboardLayer1Response.CategoryStatus::name, collator))
            .toList();

        BigDecimal spent = budget.categories().stream().map(BudgetResponse.CategoryBudget::spent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate first = today.withDayOfMonth(1), last = today.withDayOfMonth(today.lengthOfMonth());
        BigDecimal spentToDate = total(transactionRepository.sumSpentByCategory(userId, first, today));
        BigDecimal scheduled = today.isBefore(last)
            ? total(transactionRepository.sumSpentByCategory(userId, today.plusDays(1), last)) : BigDecimal.ZERO;
        BigDecimal committed = total(transactionRepository.sumCommittedByCategory(userId, first, last));
        int daysLeft = today.lengthOfMonth() - today.getDayOfMonth();
        BigDecimal pace = spentToDate.multiply(BigDecimal.valueOf(daysLeft))
            .divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_EVEN);
        BigDecimal projected = budget.totalAvailable().subtract(pace).subtract(scheduled).subtract(committed)
            .setScale(2, RoundingMode.HALF_EVEN);

        // Story 8.7: the main goal is the oldest one not completed; none left hides the card
        DashboardLayer1Response.MainGoal mainGoal = goalService.listActive(userId).stream()
            .filter(g -> !g.isCompleted()).findFirst().map(DashboardService::mainGoal).orElse(null);
        return new DashboardLayer1Response(budget.totalAvailable(), categories, mainGoal, spent,
            budget.totalAllocated(), projected, wealthHistory(userId, today, budget.totalAvailable()));
    }

    /**
     * Story 7.5. Spending per child category (Epic 6 rule) of the current month with its allocation and status, plus
     * the last 12 months per category, from one query. A category no longer active is left out; uncategorized
     * spending (null id) is "Não classificado".
     */
    @Transactional(readOnly = true)
    public DashboardLayer2Response tactical(UUID userId) {
        return dashboardCache.get(DashboardCache.Layer.LAYER2, userId, null, DashboardLayer2Response.class,
            () -> computeTactical(userId));
    }

    private DashboardLayer2Response computeTactical(UUID userId) {
        YearMonth current = YearMonth.from(LocalDate.now(clock.withZone(SAO_PAULO)));
        YearMonth first = current.minusMonths(11);
        Map<UUID, Category> active = categoryService.listActive(userId).stream()
            .collect(Collectors.toMap(Category::getId, c -> c));

        // categoryId (null = uncategorized) -> month -> spent; insertion order follows the query ORDER BY (stable output)
        Map<UUID, Map<YearMonth, BigDecimal>> spent = new LinkedHashMap<>();
        for (Object[] r : transactionRepository.sumSpentByCategoryAndMonth(userId, first.atDay(1),
                current.atEndOfMonth())) {
            UUID id = (UUID) r[0];
            if (id != null && !active.containsKey(id)) continue;
            YearMonth month = YearMonth.of(((Number) r[1]).intValue(), ((Number) r[2]).intValue());
            spent.computeIfAbsent(id, k -> new HashMap<>()).put(month, (BigDecimal) r[3]);
        }

        Map<UUID, BudgetResponse.CategoryBudget> budget = new HashMap<>();
        budgetService.get(userId, current.toString()).categories().forEach(c -> budget.put(c.categoryId(), c));

        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        List<DashboardLayer2Response.CategorySpending> categories = new ArrayList<>();
        spent.forEach((id, months) -> {
            BigDecimal value = months.get(current);
            if (value == null) return;
            BudgetResponse.CategoryBudget b = id == null ? null : budget.get(id);
            categories.add(new DashboardLayer2Response.CategorySpending(id, name(id, active),
                id == null ? null : active.get(id).getColorHex(), value, b == null ? null : b.allocated(),
                b == null ? null : b.status()));
        });
        budget.values().stream()
            .filter(b -> b.allocated() != null && active.containsKey(b.categoryId())
                && (!spent.containsKey(b.categoryId()) || !spent.get(b.categoryId()).containsKey(current)))
            .forEach(b -> categories.add(new DashboardLayer2Response.CategorySpending(b.categoryId(),
                name(b.categoryId(), active), active.get(b.categoryId()).getColorHex(), BigDecimal.ZERO,
                b.allocated(), b.status())));
        categories.sort(Comparator.comparing(DashboardLayer2Response.CategorySpending::spent, Comparator.reverseOrder())
            .thenComparing(DashboardLayer2Response.CategorySpending::name, collator));

        List<DashboardLayer2Response.CategoryTrend> trends = new ArrayList<>();
        spent.forEach((id, months) -> {
            List<DashboardLayer2Response.MonthSpent> series = new ArrayList<>();
            for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
                series.add(new DashboardLayer2Response.MonthSpent(m.toString(), months.getOrDefault(m, BigDecimal.ZERO)));
            }
            trends.add(new DashboardLayer2Response.CategoryTrend(id, name(id, active), series));
        });
        return new DashboardLayer2Response(current.toString(), categories, trends);
    }

    /**
     * Story 7.6, the filters of the layer 3. {@code from}/{@code to} are "YYYY-MM-DD" (blank = the current month's bound
     * in São Paulo). A transaction matches the categories when it is in {@code categoryIds} or, with
     * {@code uncategorized}, has none; the same for {@code accountIds}/{@code creditCardIds}; an empty pair is no filter.
     * {@code sort} is "field,dir" with field date, amount or category (null = "date,desc").
     */
    public record DetailsFilter(String from, String to, List<UUID> categoryIds, boolean uncategorized,
                                List<UUID> accountIds, List<UUID> creditCardIds, String q, String sort) {
        public DetailsFilter {
            categoryIds = categoryIds == null ? List.of() : categoryIds;
            accountIds = accountIds == null ? List.of() : accountIds;
            creditCardIds = creditCardIds == null ? List.of() : creditCardIds;
        }
    }

    /** Story 7.6: page {@code page} (0-based, 50 rows) of the filtered list, same base as /transacoes. */
    @Transactional(readOnly = true)
    public DashboardLayer3Response details(UUID userId, DetailsFilter filter, int page) {
        if (page < 0) throw new BadRequestException(INVALID_PAGE_MESSAGE, "page");
        return dashboardCache.get(DashboardCache.Layer.LAYER3, userId, filtersHash(filter, page),
            DashboardLayer3Response.class, () -> computeDetails(userId, filter, page));
    }

    private DashboardLayer3Response computeDetails(UUID userId, DetailsFilter filter, int page) {
        Page<Transaction> rows = transactionRepository.findAll(detailsSpec(userId, filter),
            PageRequest.of(page, DETAILS_PAGE_SIZE));
        return new DashboardLayer3Response(transactionService.toResponses(userId, rows.getContent()), page,
            rows.getTotalElements(), rows.getTotalPages());
    }

    /** Story 7.7: SHA-256 of the whole filter, the page and the current month (blank dates mean the current month). */
    String filtersHash(DetailsFilter f, int page) {
        StringBuilder text = new StringBuilder();
        // length-prefixed parts: no free text (q) can pass for another field; null is "-1:", which no string gives
        for (Object part : java.util.Arrays.asList(f.from(), f.to(), f.categoryIds(), f.uncategorized(),
                f.accountIds(), f.creditCardIds(), f.q(), f.sort(), page,
                YearMonth.from(LocalDate.now(clock.withZone(SAO_PAULO))))) {
            String value = part == null ? "" : part.toString();
            text.append(part == null ? -1 : value.length()).append(':').append(value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Story 7.6: every row of the filtered list in the same order, as CSV for Excel pt-BR (BOM, ";", decimal comma).
     * The value is signed (debit negative); origin is the import or a manual entry.
     */
    @Transactional(readOnly = true)
    public String detailsCsv(UUID userId, DetailsFilter filter) {
        List<Transaction> rows = transactionRepository.findAll(detailsSpec(userId, filter));
        List<TransactionResponse> responses = transactionService.toResponses(userId, rows);
        Map<UUID, String> targets = new HashMap<>();
        accountRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(a -> targets.put(a.getId(), a.getAccountName()));
        creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId).forEach(c -> targets.put(c.getId(), c.getCardName()));

        StringBuilder csv = new StringBuilder("﻿Data;Descrição;Valor;Tipo;Categoria;Conta;Pix;Parcela;Origem\r\n");
        for (int i = 0; i < rows.size(); i++) {
            Transaction row = rows.get(i);
            TransactionResponse r = responses.get(i);
            boolean debit = row.getTransactionType() == Transaction.Type.DEBIT;
            UUID target = row.getAccountId() != null ? row.getAccountId() : row.getCreditCardId();
            csv.append(String.join(";",
                row.getTransactionDate().format(CSV_DATE),
                csvText(row.getDescription()),
                (debit ? row.getAmount().negate() : row.getAmount()).toPlainString().replace('.', ','),
                debit ? "Saída" : "Entrada",
                csvText(categoryLabel(row, r.category())),
                csvText(targets.getOrDefault(target, "")),
                row.isPix() ? "Sim" : "Não",
                row.isInstallment() && row.getInstallmentNumber() != null && row.getInstallmentTotal() != null
                    ? row.getInstallmentNumber() + "/" + row.getInstallmentTotal() : "",
                row.getImportId() != null ? "Importação" : "Manual")).append("\r\n");
        }
        return csv.toString();
    }

    /** Same label as /transacoes: "Grupo › Categoria", card payments and uncategorized by name. */
    private static String categoryLabel(Transaction row, com.porquinho.dto.ImportTransactionResponse.Category category) {
        if (row.getKind() == Transaction.Kind.CARD_PAYMENT) return "Pagamento de fatura";
        if (category == null) return "Não classificado";
        return category.group() == null ? category.name() : category.group() + " › " + category.name();
    }

    /** Quotes a field with ; " or a line break; a leading = + - @ gets a ' so a spreadsheet does not run it. */
    static String csvText(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Base of /transacoes (findConfirmedBetween) plus the optional filters; the order is skipped on the count query. */
    private Specification<Transaction> detailsSpec(UUID userId, DetailsFilter f) {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        LocalDate from = date(f.from(), today.withDayOfMonth(1), "from");
        LocalDate to = date(f.to(), today.withDayOfMonth(today.lengthOfMonth()), "to");
        if (from.isAfter(to)) throw new BadRequestException(INVALID_PERIOD_MESSAGE, "from");
        String[] sort = (f.sort() == null || f.sort().isBlank() ? "date,desc" : f.sort()).split(",", -1);
        if (sort.length != 2 || !List.of("date", "amount", "category").contains(sort[0])
            || !List.of("asc", "desc").contains(sort[1])) {
            throw new BadRequestException(INVALID_SORT_MESSAGE, "sort");
        }
        String field = sort[0];
        boolean asc = sort[1].equals("asc");
        String q = f.q() == null ? "" : f.q().strip().toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("userId"), userId));
            where.add(cb.equal(root.get("reviewStatus"), Transaction.ReviewStatus.CONFIRMED));
            where.add(cb.isFalse(root.get("futureInstallment")));
            where.add(cb.isNull(root.get("deletedAt")));
            where.add(cb.between(root.get("transactionDate"), from, to));
            List<Predicate> categories = new ArrayList<>();
            if (!f.categoryIds().isEmpty()) categories.add(root.get("categoryId").in(f.categoryIds()));
            // "Não classificado" as the list shows it: a card payment without category is "Pagamento de fatura"
            if (f.uncategorized()) categories.add(cb.and(cb.isNull(root.get("categoryId")),
                cb.notEqual(root.get("kind"), Transaction.Kind.CARD_PAYMENT)));
            if (!categories.isEmpty()) where.add(cb.or(categories.toArray(Predicate[]::new)));
            List<Predicate> targets = new ArrayList<>();
            if (!f.accountIds().isEmpty()) targets.add(root.get("accountId").in(f.accountIds()));
            if (!f.creditCardIds().isEmpty()) targets.add(root.get("creditCardId").in(f.creditCardIds()));
            if (!targets.isEmpty()) where.add(cb.or(targets.toArray(Predicate[]::new)));
            if (!q.isEmpty()) {
                String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.like(cb.lower(root.get("description")), pattern, '\\'));
            }

            Class<?> result = query.getResultType();
            if (result != Long.class && result != long.class) {
                List<Expression<?>> keys = new ArrayList<>();
                switch (field) {
                    case "amount" -> keys.add(root.get("amount"));
                    case "category" -> {
                        // same order as the label "Grupo › Categoria": group, then child
                        var category = ((JpaRoot<Transaction>) root).join(Category.class, SqmJoinType.LEFT);
                        category.on(cb.equal(category.get("id"), root.get("categoryId")));
                        var group = ((JpaRoot<Transaction>) root).join(Category.class, SqmJoinType.LEFT);
                        group.on(cb.equal(group.get("id"), category.get("parentCategoryId")));
                        keys.add(cb.lower(group.get("categoryName")));
                        keys.add(cb.lower(category.get("categoryName")));
                    }
                    default -> keys.add(root.get("transactionDate"));
                }
                List<Order> orders = new ArrayList<>();
                keys.forEach(k -> orders.add(asc ? cb.asc(k) : cb.desc(k)));
                // stable tie-break: newest date, then id
                orders.add(cb.desc(root.get("transactionDate")));
                orders.add(cb.desc(root.get("id")));
                query.orderBy(orders);
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static LocalDate date(String value, LocalDate fallback, String field) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_PERIOD_MESSAGE, field);
        }
    }

    private static String name(UUID id, Map<UUID, Category> active) {
        return id == null ? "Não classificado" : active.get(id).getCategoryName();
    }

    /**
     * Story 7.4: the sum of the account balances over the last 12 months. An account is worth its last snapshot up to
     * that instant (0 before the first one) and 0 from its deleted_at on (a restored account counts all along). Every
     * point is the value at the end of its São Paulo day, the first one (12 months ago) included; today is
     * {@code totalBalance}. Two queries for the whole user, no per-account lookups.
     */
    private List<DashboardLayer1Response.WealthPoint> wealthHistory(UUID userId, LocalDate today, BigDecimal now) {
        LocalDate start = today.minusMonths(12);
        OffsetDateTime cutoff = endOfDay(start);
        List<AccountBalanceHistory> before = balanceHistoryRepository.findLastPerAccountBefore(userId, cutoff);
        List<AccountBalanceHistory> since = balanceHistoryRepository.findByUserSince(userId, cutoff);
        if (before.isEmpty() && since.isEmpty()) return List.of();

        Map<UUID, OffsetDateTime> deletedAt = new HashMap<>();
        for (Account a : accountRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (a.getDeletedAt() != null) deletedAt.put(a.getId(), a.getDeletedAt());
        }
        Map<UUID, BigDecimal> last = new HashMap<>();
        before.forEach(h -> last.put(h.getAccountId(), h.getBalanceSnapshot()));

        // Days with a change between the first day and today (both exclusive); a removal alone is a change too
        TreeMap<LocalDate, List<AccountBalanceHistory>> days = new TreeMap<>();
        for (AccountBalanceHistory h : since) {
            LocalDate day = h.getSnapshotDate().atZoneSameInstant(SAO_PAULO).toLocalDate();
            if (day.isBefore(today)) days.computeIfAbsent(day, d -> new ArrayList<>()).add(h);
        }
        deletedAt.values().forEach(at -> {
            LocalDate day = at.atZoneSameInstant(SAO_PAULO).toLocalDate();
            if (day.isAfter(start) && day.isBefore(today)) days.computeIfAbsent(day, d -> new ArrayList<>());
        });

        List<DashboardLayer1Response.WealthPoint> points = new ArrayList<>();
        points.add(new DashboardLayer1Response.WealthPoint(start, sum(last, deletedAt, cutoff)));
        days.forEach((day, snapshots) -> {
            snapshots.forEach(h -> last.put(h.getAccountId(), h.getBalanceSnapshot()));
            points.add(new DashboardLayer1Response.WealthPoint(day, sum(last, deletedAt, endOfDay(day))));
        });
        points.add(new DashboardLayer1Response.WealthPoint(today, now));
        return points;
    }

    private static OffsetDateTime endOfDay(LocalDate day) {
        return day.plusDays(1).atStartOfDay(SAO_PAULO).toOffsetDateTime();
    }

    /** Sum of the last balances at {@code at}, an account removed before that instant being worth 0. */
    private static BigDecimal sum(Map<UUID, BigDecimal> last, Map<UUID, OffsetDateTime> deletedAt, OffsetDateTime at) {
        return last.entrySet().stream()
            .filter(e -> !deletedAt.containsKey(e.getKey()) || deletedAt.get(e.getKey()).isAfter(at))
            .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of every {categoryId, sum} row, uncategorized (null id) included. */
    private static BigDecimal total(List<Object[]> rows) {
        return rows.stream().map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static DashboardLayer1Response.MainGoal mainGoal(Goal goal) {
        BigDecimal target = goal.getTargetAmount();
        int percent = target.signum() <= 0 ? 100 : goal.getCurrentAmount().multiply(BigDecimal.valueOf(100))
            .divide(target, 0, RoundingMode.FLOOR).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).intValue();
        return new DashboardLayer1Response.MainGoal(goal.getGoalName(), goal.getCurrentAmount(), target, percent);
    }

    /** Story 8.4 rule: today + ceil(missing / monthly allocation) months past the target date. */
    static boolean isBehind(Goal goal, LocalDate today) {
        BigDecimal allocation = goal.getMonthlyAllocation();
        if (goal.getTargetDate() == null || allocation == null || allocation.signum() <= 0) return false;
        BigDecimal missing = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        if (missing.signum() <= 0) return false;
        long months = missing.divide(allocation, 0, RoundingMode.CEILING).longValueExact();
        // An absurd projection would overflow LocalDate; past the month bound (+1 for end-of-month clamping) it is late
        if (months > ChronoUnit.MONTHS.between(today, goal.getTargetDate()) + 1) return true;
        return today.plusMonths(months).isAfter(goal.getTargetDate());
    }
}
