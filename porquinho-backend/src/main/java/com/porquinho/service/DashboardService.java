package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Goal;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** Dashboard GPS (Epic 7). Story 7.2: the financial health traffic light. Story 7.3: the 5 essential metrics.
 * Story 7.4: the wealth curve inside the layer 1. */
@Service
public class DashboardService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final BudgetService budgetService;
    private final AccountService accountService;
    private final GoalService goalService;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceHistoryRepository balanceHistoryRepository;
    private final Clock clock;

    public DashboardService(BudgetService budgetService, AccountService accountService, GoalService goalService,
                            CategoryService categoryService, TransactionRepository transactionRepository,
                            AccountRepository accountRepository,
                            AccountBalanceHistoryRepository balanceHistoryRepository, Clock clock) {
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
        int goalsBehind = (int) goalService.listActive(userId).stream().filter(g -> isBehind(g, today)).count();

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
     * keeps the budget screen's number (whole month, categories only). The main goal is the oldest active one.
     */
    @Transactional(readOnly = true)
    public DashboardLayer1Response overview(UUID userId) {
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

        List<Goal> goals = goalService.listActive(userId);
        DashboardLayer1Response.MainGoal mainGoal = goals.isEmpty() ? null : mainGoal(goals.get(0));
        return new DashboardLayer1Response(budget.totalAvailable(), categories, mainGoal, spent,
            budget.totalAllocated(), projected, wealthHistory(userId, today, budget.totalAvailable()));
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
