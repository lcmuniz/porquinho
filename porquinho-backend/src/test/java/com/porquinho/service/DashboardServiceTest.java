package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.DashboardLayer2Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.entity.Account;
import com.porquinho.entity.Account.AccountType;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Category;
import com.porquinho.entity.Goal;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 7.2: the traffic light rule, one line of the spec's matrix per test plus the 80/50 limits. Story 7.3: the 5
 * metrics (category order, goal %, projection on the first, a middle and the last day of the month). Story 7.4: the
 * wealth curve, one line of the spec's matrix per test. Story 7.5: the tactical view (order, "Não classificado",
 * allocation and status, 12 months with zeros).
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    // 2026-12-01 01:00 UTC is still 2026-11-30 in São Paulo
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);

    @Mock
    private BudgetService budgetService;
    @Mock
    private AccountService accountService;
    @Mock
    private GoalService goalService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    private DashboardService service;
    private UUID userId;
    private final List<Account> accounts = new ArrayList<>();
    private final List<Goal> goals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DashboardService(budgetService, accountService, goalService, categoryService, transactionRepository,
            accountRepository, balanceHistoryRepository, null, null, CLOCK);
        userId = UUID.randomUUID();
        // the overview (7.3) reads the total from the budget, not the accounts
        lenient().when(accountService.listActive(userId)).thenReturn(accounts);
        // the tactical view (7.5) reads no goal
        lenient().when(goalService.listActive(userId)).thenReturn(goals);
    }

    private void budget(int healthy, int low, int overspent) {
        List<BudgetResponse.CategoryBudget> categories = new ArrayList<>();
        // An unbudgeted category (status null) never counts
        categories.add(new BudgetResponse.CategoryBudget(UUID.randomUUID(), null, BigDecimal.ZERO, null, null, null));
        for (int i = 0; i < healthy; i++) categories.add(category(BudgetStatus.HEALTHY));
        for (int i = 0; i < low; i++) categories.add(category(BudgetStatus.LOW));
        for (int i = 0; i < overspent; i++) categories.add(category(BudgetStatus.OVERSPENT));
        when(budgetService.get(userId, "2026-11"))
            .thenReturn(new BudgetResponse("2026-11", "v1", BigDecimal.ZERO, BigDecimal.ZERO, categories));
    }

    private static BudgetResponse.CategoryBudget category(BudgetStatus status) {
        return new BudgetResponse.CategoryBudget(UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE, null,
            BigDecimal.ONE, status);
    }

    private void account(String balance) {
        Account account = new Account(userId, "Conta", null, AccountType.CHECKING, new BigDecimal(balance));
        accounts.add(account);
    }

    private static Goal goal(String target, String current, LocalDate date, String allocation) {
        Goal goal = new Goal(UUID.randomUUID(), "Reserva", new BigDecimal(target), date,
            allocation == null ? null : new BigDecimal(allocation));
        goal.setCurrentAmount(new BigDecimal(current));
        return goal;
    }

    private HealthStatus status() {
        return service.health(userId).status();
    }

    @Test
    void healthyWithNineHealthyAndOneLow() {
        budget(9, 1, 0);
        account("100.00");
        goals.add(goal("1000", "0", TODAY.plusMonths(12), "100"));

        FinancialHealthResponse response = service.health(userId);

        assertEquals(new FinancialHealthResponse(HealthStatus.HEALTHY, 9, 1, 0, 0, 0), response);
        verify(budgetService).get(userId, "2026-11");
    }

    @Test
    void exactly80PercentIsHealthy() {
        budget(8, 2, 0);
        assertEquals(HealthStatus.HEALTHY, status());
    }

    @Test
    void justUnder80PercentIsAttention() {
        budget(79, 21, 0);
        assertEquals(HealthStatus.ATTENTION, status());
    }

    @Test
    void oneOverspentIsAttention() {
        budget(9, 0, 1);
        assertEquals(HealthStatus.ATTENTION, status());
    }

    @Test
    void twoOverspentIsCritical() {
        budget(8, 0, 2);
        assertEquals(HealthStatus.CRITICAL, status());
    }

    @Test
    void between50And79PercentIsAttention() {
        budget(6, 4, 0);
        assertEquals(HealthStatus.ATTENTION, status());
    }

    @Test
    void exactly50PercentIsAttention() {
        budget(5, 5, 0);
        assertEquals(HealthStatus.ATTENTION, status());
    }

    @Test
    void under50PercentIsCritical() {
        budget(4, 6, 0);
        assertEquals(HealthStatus.CRITICAL, status());
    }

    @Test
    void aNegativeAccountIsCritical() {
        budget(10, 0, 0);
        account("50.00");
        account("-10.00");

        FinancialHealthResponse response = service.health(userId);

        assertEquals(HealthStatus.CRITICAL, response.status());
        assertEquals(1, response.negativeAccounts());
    }

    @Test
    void aZeroAccountIsNotNegative() {
        budget(10, 0, 0);
        account("0.00");

        FinancialHealthResponse response = service.health(userId);

        assertEquals(HealthStatus.HEALTHY, response.status());
        assertEquals(0, response.negativeAccounts());
    }

    @Test
    void aGoalBehindIsAttention() {
        budget(10, 0, 0);
        // 1000 missing at 100/month = 10 months, target in 6
        goals.add(goal("1500", "500", TODAY.plusMonths(6), "100"));

        FinancialHealthResponse response = service.health(userId);

        assertEquals(HealthStatus.ATTENTION, response.status());
        assertEquals(1, response.goalsBehind());
    }

    @Test
    void aGoalProjectedExactlyOnTheTargetDateIsOnTrack() {
        budget(10, 0, 0);
        // 950 missing at 100/month rounds up to 10 months, target exactly in 10
        goals.add(goal("1000", "50", TODAY.plusMonths(10), "100"));
        assertEquals(HealthStatus.HEALTHY, status());
    }

    @Test
    void aHugeProjectionCountsAsBehindWithoutOverflowingTheDate() {
        budget(10, 0, 0);
        goals.add(goal("9999999999999.99", "0", TODAY.plusMonths(12), "0.01"));

        FinancialHealthResponse response = service.health(userId);

        assertEquals(HealthStatus.ATTENTION, response.status());
        assertEquals(1, response.goalsBehind());
    }

    @Test
    void goalsWithoutAllocationOrDateOrAlreadyReachedAreLeftOut() {
        budget(10, 0, 0);
        goals.add(goal("25000", "0", TODAY.plusMonths(1), null));
        goals.add(goal("25000", "0", TODAY.plusMonths(1), "0"));
        goals.add(goal("25000", "0", null, "100"));
        goals.add(goal("1000", "1000", TODAY.minusMonths(1), "100"));

        FinancialHealthResponse response = service.health(userId);

        assertEquals(HealthStatus.HEALTHY, response.status());
        assertEquals(0, response.goalsBehind());
    }

    @Test
    void nothingBudgetedIsHealthyWhenAccountsAndGoalsAreFine() {
        budget(0, 0, 0);
        account("10.00");

        assertEquals(new FinancialHealthResponse(HealthStatus.HEALTHY, 0, 0, 0, 0, 0), service.health(userId));
    }

    @Test
    void nothingBudgetedStillTurnsCriticalOnANegativeAccount() {
        budget(0, 0, 0);
        account("-0.01");
        assertEquals(HealthStatus.CRITICAL, status());
    }

    // ---- Story 7.3: overview ----

    private final List<Category> categories = new ArrayList<>();
    private final List<BudgetResponse.CategoryBudget> budgetCategories = new ArrayList<>();
    private final List<Object[]> spentToDate = new ArrayList<>();
    private final List<Object[]> spentAfterToday = new ArrayList<>();
    private final List<Object[]> committed = new ArrayList<>();

    /** A {categoryId, sum} row of the repository; a null id is an uncategorized transaction. */
    private static Object[] row(UUID categoryId, String sum) {
        return new Object[] {categoryId, new BigDecimal(sum)};
    }

    private void budgeted(String name, String allocated, String spent, String committed, BudgetStatus status) {
        Category category = new Category(userId, name, UUID.randomUUID(), "#000000", "tag", false);
        category.setId(UUID.randomUUID());
        categories.add(category);
        BigDecimal alloc = allocated == null ? null : new BigDecimal(allocated);
        BigDecimal spentValue = new BigDecimal(spent);
        budgetCategories.add(new BudgetResponse.CategoryBudget(category.getId(), alloc, spentValue,
            new BigDecimal(committed), alloc == null ? null : alloc.subtract(spentValue), status));
    }

    /** Overview on the given São Paulo day, total available as given, total allocated = sum of allocations. */
    private DashboardLayer1Response overview(LocalDate day, String totalAvailable) {
        BigDecimal allocated = budgetCategories.stream().map(BudgetResponse.CategoryBudget::allocated)
            .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        when(budgetService.get(userId, "2026-11")).thenReturn(new BudgetResponse("2026-11", "v1",
            new BigDecimal(totalAvailable), allocated, budgetCategories));
        when(categoryService.listActive(userId)).thenReturn(categories);
        Clock clock = Clock.fixed(day.atTime(12, 0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant(),
            ZoneOffset.UTC);
        LocalDate first = day.withDayOfMonth(1), last = day.withDayOfMonth(day.lengthOfMonth());
        lenient().when(transactionRepository.sumSpentByCategory(userId, first, day)).thenReturn(spentToDate);
        lenient().when(transactionRepository.sumSpentByCategory(userId, day.plusDays(1), last))
            .thenReturn(spentAfterToday);
        lenient().when(transactionRepository.sumCommittedByCategory(userId, first, last)).thenReturn(committed);
        return new DashboardService(budgetService, accountService, goalService, categoryService, transactionRepository,
            accountRepository, balanceHistoryRepository, null, null, clock).overview(userId);
    }

    @Test
    void overviewFillsTheFiveMetrics() {
        budgeted("Mercado", "500.00", "100.00", "0.00", BudgetStatus.HEALTHY);
        budgeted("Lazer", "200.00", "50.00", "0.00", BudgetStatus.HEALTHY);
        budgeted("Casa", "300.00", "0.00", "0.00", BudgetStatus.HEALTHY);
        goals.add(goal("1000", "250", null, null));

        DashboardLayer1Response response = overview(LocalDate.of(2026, 11, 30), "1500.00");

        assertEquals(new BigDecimal("1500.00"), response.totalBalance());
        assertEquals(List.of("Casa", "Lazer", "Mercado"),
            response.categories().stream().map(DashboardLayer1Response.CategoryStatus::name).toList());
        assertEquals(new DashboardLayer1Response.MainGoal("Reserva", new BigDecimal("250"), new BigDecimal("1000"), 25),
            response.mainGoal());
        assertEquals(new BigDecimal("150.00"), response.monthSpent());
        assertEquals(new BigDecimal("1000.00"), response.monthAllocated());
        // last day: no days left, nothing committed
        assertEquals(new BigDecimal("1500.00"), response.projectedBalance());
        verify(budgetService).get(userId, "2026-11");
    }

    @Test
    void overspentFirstThenLowThenHealthyByNameAndUnbudgetedLeftOut() {
        budgeted("Água", "100.00", "10.00", "0.00", BudgetStatus.HEALTHY);
        budgeted("Zoo", "100.00", "90.00", "0.00", BudgetStatus.LOW);
        budgeted("Bar", "100.00", "150.00", "0.00", BudgetStatus.OVERSPENT);
        budgeted("Academia", "100.00", "95.00", "0.00", BudgetStatus.LOW);
        budgeted("Sem orçamento", null, "40.00", "0.00", null);

        DashboardLayer1Response response = overview(LocalDate.of(2026, 11, 30), "0.00");

        assertEquals(List.of("Bar", "Academia", "Zoo", "Água"),
            response.categories().stream().map(DashboardLayer1Response.CategoryStatus::name).toList());
        assertEquals(new BigDecimal("-50.00"), response.categories().get(0).remaining());
        // an unbudgeted category's spending still counts
        assertEquals(new BigDecimal("385.00"), response.monthSpent());
    }

    @Test
    void nothingBudgetedAndNoGoal() {
        DashboardLayer1Response response = overview(LocalDate.of(2026, 11, 30), "10.00");

        assertEquals(List.of(), response.categories());
        assertNull(response.mainGoal());
        assertEquals(BigDecimal.ZERO, response.monthSpent());
        assertEquals(BigDecimal.ZERO, response.monthAllocated());
    }

    @Test
    void theMainGoalIsTheOldestAndCapsAt100Percent() {
        goals.add(goal("1000", "1200", null, null));
        Goal newer = goal("1000", "0", null, null);
        newer.setGoalName("Viagem");
        goals.add(newer);

        assertEquals(100, overview(LocalDate.of(2026, 11, 30), "0.00").mainGoal().percent());
    }

    @Test
    void goalPercentRoundsDown() {
        goals.add(goal("3", "2", null, null));
        assertEquals(66, overview(LocalDate.of(2026, 11, 30), "0.00").mainGoal().percent());
    }

    @Test
    void projectionOnTheTenthOfA30DayMonth() {
        // 3000 - 1000 / 10 x 20 - 200 = 800
        spentToDate.add(row(UUID.randomUUID(), "1000.00"));
        committed.add(row(UUID.randomUUID(), "200.00"));
        assertEquals(new BigDecimal("800.00"), overview(LocalDate.of(2026, 11, 10), "3000.00").projectedBalance());
    }

    @Test
    void uncategorizedSpendingCountsInTheProjectionButNotInTheSpentCard() {
        // matrix: 1000 spent, 400 of it without category; installments of 200 as in the projection line
        budgeted("Mercado", "2000.00", "600.00", "0.00", BudgetStatus.HEALTHY);
        spentToDate.add(row(budgetCategories.get(0).categoryId(), "600.00"));
        spentToDate.add(row(null, "400.00"));
        committed.add(row(null, "200.00"));

        DashboardLayer1Response response = overview(LocalDate.of(2026, 11, 10), "3000.00");

        assertEquals(new BigDecimal("600.00"), response.monthSpent());
        assertEquals(new BigDecimal("800.00"), response.projectedBalance());
    }

    @Test
    void aDebitDatedLaterInTheMonthIsNotPaceButIsSubtracted() {
        // 3000 - 1000 / 10 x 20 - 300 (on the 25th) = 700
        spentToDate.add(row(UUID.randomUUID(), "1000.00"));
        spentAfterToday.add(row(null, "300.00"));
        assertEquals(new BigDecimal("700.00"), overview(LocalDate.of(2026, 11, 10), "3000.00").projectedBalance());
    }

    @Test
    void projectionOnTheFirstDayOfTheMonth() {
        // 3000 - 30 / 1 x 29 - 100 = 2030
        spentToDate.add(row(UUID.randomUUID(), "30.00"));
        committed.add(row(UUID.randomUUID(), "100.00"));
        assertEquals(new BigDecimal("2030.00"), overview(LocalDate.of(2026, 11, 1), "3000.00").projectedBalance());
    }

    @Test
    void projectionOnTheLastDayOfTheMonthOnlySubtractsTheInstallments() {
        spentToDate.add(row(UUID.randomUUID(), "1000.00"));
        committed.add(row(UUID.randomUUID(), "50.00"));
        assertEquals(new BigDecimal("2950.00"), overview(LocalDate.of(2026, 11, 30), "3000.00").projectedBalance());
    }

    @Test
    void projectionRoundsHalfEvenToTwoPlaces() {
        // 0.01 x 26 / 4 = 0.065 -> 0.06 (HALF_UP would give 0.07)
        spentToDate.add(row(UUID.randomUUID(), "0.01"));
        assertEquals(new BigDecimal("99.94"), overview(LocalDate.of(2026, 11, 4), "100.00").projectedBalance());
    }

    @Test
    void categoriesSortByNameInPortuguese() {
        budgeted("Zoo", "100.00", "0.00", "0.00", BudgetStatus.HEALTHY);
        budgeted("mercado", "100.00", "0.00", "0.00", BudgetStatus.HEALTHY);
        budgeted("Água", "100.00", "0.00", "0.00", BudgetStatus.HEALTHY);

        assertEquals(List.of("Água", "mercado", "Zoo"), overview(LocalDate.of(2026, 11, 30), "0.00").categories()
            .stream().map(DashboardLayer1Response.CategoryStatus::name).toList());
    }

    @Test
    void overviewUsesTheSaoPauloDay() {
        // CLOCK is 2026-12-01 01:00 UTC, still 2026-11-30 (last day) in São Paulo
        when(budgetService.get(userId, "2026-11"))
            .thenReturn(new BudgetResponse("2026-11", "v1", new BigDecimal("3000.00"), BigDecimal.ZERO, List.of()));
        when(categoryService.listActive(userId)).thenReturn(List.of());
        LocalDate first = LocalDate.of(2026, 11, 1);
        when(transactionRepository.sumSpentByCategory(userId, first, TODAY))
            .thenReturn(List.<Object[]>of(row(null, "1000.00")));
        when(transactionRepository.sumCommittedByCategory(userId, first, TODAY))
            .thenReturn(List.<Object[]>of(row(null, "50.00")));

        assertEquals(new BigDecimal("2950.00"), service.overview(userId).projectedBalance());
        verify(budgetService).get(userId, "2026-11");
        verify(transactionRepository, never()).sumSpentByCategory(eq(userId), eq(TODAY.plusDays(1)), any());
    }

    // ---- Story 7.4: wealth history ----

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // TODAY = 2026-11-30 in São Paulo: the 12 months start on 2025-11-30, the 3 months on 2026-08-30
    private static final LocalDate START = TODAY.minusMonths(12);
    private final List<AccountBalanceHistory> before = new ArrayList<>();
    private final List<AccountBalanceHistory> since = new ArrayList<>();
    private final List<Account> allAccounts = new ArrayList<>();

    private UUID newAccount(OffsetDateTime deletedAt) {
        Account account = new Account(userId, "Conta", null, AccountType.CHECKING, BigDecimal.ZERO);
        account.setId(UUID.randomUUID());
        account.setDeletedAt(deletedAt);
        allAccounts.add(account);
        return account.getId();
    }

    /** A snapshot at {@code hour}:00 (São Paulo) of the given day; goes to "before" when it is on or before the first day. */
    private void snapshot(UUID accountId, LocalDate day, int hour, String balance) {
        AccountBalanceHistory h = new AccountBalanceHistory(accountId, userId, new BigDecimal(balance), "edit");
        h.setSnapshotDate(day.atTime(hour, 0).atZone(SAO_PAULO).toOffsetDateTime());
        h.setId(UUID.randomUUID());
        if (day.isAfter(START)) {
            since.add(h);
        } else {
            before.removeIf(b -> b.getAccountId().equals(accountId));
            before.add(h);
        }
    }

    private List<DashboardLayer1Response.WealthPoint> wealth(String totalBalance) {
        OffsetDateTime cutoff = START.plusDays(1).atStartOfDay(SAO_PAULO).toOffsetDateTime();
        when(balanceHistoryRepository.findLastPerAccountBefore(userId, cutoff)).thenReturn(before);
        when(balanceHistoryRepository.findByUserSince(userId, cutoff)).thenReturn(since);
        lenient().when(accountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(allAccounts);
        return overview(TODAY, totalBalance).wealthHistory();
    }

    private static DashboardLayer1Response.WealthPoint point(LocalDate day, String balance) {
        return new DashboardLayer1Response.WealthPoint(day, new BigDecimal(balance));
    }

    @Test
    void wealthSumsTheAccountsAndANewAccountIsZeroBeforeItsFirstSnapshot() {
        UUID a = newAccount(null), b = newAccount(null);
        snapshot(a, TODAY.minusMonths(14), 12, "1000.00");
        snapshot(b, TODAY.minusMonths(2), 12, "500.00");
        snapshot(a, TODAY.minusMonths(1), 12, "1500.00");

        assertEquals(List.of(point(START, "1000.00"), point(TODAY.minusMonths(2), "1500.00"),
            point(TODAY.minusMonths(1), "2000.00"), point(TODAY, "2000.00")), wealth("2000.00"));
    }

    @Test
    void theLastPointIsTodayWithTheTotalBalance() {
        UUID a = newAccount(null);
        snapshot(a, TODAY.minusMonths(13), 12, "1000.00");
        // today's own snapshots do not make a point: today is the total balance
        snapshot(a, TODAY, 10, "1234.56");

        assertEquals(List.of(point(START, "1000.00"), point(TODAY, "1234.56")), wealth("1234.56"));
    }

    @Test
    void withoutAnySnapshotTheHistoryIsEmpty() {
        assertEquals(List.of(), wealth("0.00"));
    }

    @Test
    void manyEventsOnOneDayMakeOnePointWithTheLastBalance() {
        UUID a = newAccount(null);
        LocalDate day = TODAY.minusDays(10);
        snapshot(a, day, 9, "1000.00");
        snapshot(a, day, 12, "1200.00");
        snapshot(a, day, 18, "900.00");

        assertEquals(List.of(point(START, "0"), point(day, "900.00"), point(TODAY, "900.00")), wealth("900.00"));
    }

    @Test
    void aRemovedAccountCountsUntilItsRemovalAndZeroAfter() {
        LocalDate removal = TODAY.minusMonths(1);
        UUID a = newAccount(removal.atTime(15, 0).atZone(SAO_PAULO).toOffsetDateTime());
        UUID b = newAccount(null);
        snapshot(a, TODAY.minusMonths(13), 12, "1000.00");
        snapshot(b, TODAY.minusMonths(13), 12, "500.00");

        assertEquals(List.of(point(START, "1500.00"), point(removal, "500.00"), point(TODAY, "500.00")),
            wealth("500.00"));
    }

    @Test
    void anAccountRemovedBeforeThePeriodIsZeroAllAlong() {
        UUID a = newAccount(TODAY.minusMonths(13).atStartOfDay(SAO_PAULO).toOffsetDateTime());
        UUID b = newAccount(null);
        snapshot(a, TODAY.minusMonths(14), 12, "1000.00");
        snapshot(b, TODAY.minusMonths(14), 12, "200.00");

        assertEquals(List.of(point(START, "200.00"), point(TODAY, "200.00")), wealth("200.00"));
    }

    @Test
    void aNegativeSumIsKept() {
        UUID a = newAccount(null);
        snapshot(a, TODAY.minusMonths(13), 12, "100.00");
        snapshot(a, TODAY.minusDays(5), 12, "-200.00");

        assertEquals(List.of(point(START, "100.00"), point(TODAY.minusDays(5), "-200.00"), point(TODAY, "-200.00")),
            wealth("-200.00"));
    }

    @Test
    void wealthDaysUseSaoPaulo() {
        UUID a = newAccount(null);
        // 2026-11-20 23:00 in São Paulo is already the 21st in UTC: the point stays on the 20th
        snapshot(a, LocalDate.of(2026, 11, 20), 23, "700.00");

        assertEquals(List.of(point(START, "0"), point(LocalDate.of(2026, 11, 20), "700.00"), point(TODAY, "700.00")),
            wealth("700.00"));
    }

    // ---- Story 7.5: tactical view ----

    private final List<Object[]> monthly = new ArrayList<>();

    private UUID child(String name) {
        Category category = new Category(userId, name, UUID.randomUUID(), "#111111", "tag", false);
        category.setId(UUID.randomUUID());
        categories.add(category);
        return category.getId();
    }

    /** A {categoryId, year, month, sum} row of the monthly query (Hibernate gives Integer for YEAR/MONTH). */
    private void spent(UUID categoryId, int year, int month, String sum) {
        monthly.add(new Object[] {categoryId, year, month, new BigDecimal(sum)});
    }

    private DashboardLayer2Response tactical() {
        when(categoryService.listActive(userId)).thenReturn(categories);
        when(budgetService.get(userId, "2026-11")).thenReturn(new BudgetResponse("2026-11", "v1", BigDecimal.ZERO,
            BigDecimal.ZERO, budgetCategories));
        when(transactionRepository.sumSpentByCategoryAndMonth(userId, LocalDate.of(2025, 12, 1),
            LocalDate.of(2026, 11, 30))).thenReturn(monthly);
        return service.tactical(userId);
    }

    @Test
    void tacticalOrdersBySpentWithAllocationStatusAndUncategorized() {
        UUID food = child("Mercado");
        UUID fun = child("Lazer");
        UUID home = child("Casa");
        budgetCategories.add(new BudgetResponse.CategoryBudget(fun, new BigDecimal("500.00"), new BigDecimal("620.00"),
            null, new BigDecimal("-120.00"), BudgetStatus.OVERSPENT));
        budgetCategories.add(new BudgetResponse.CategoryBudget(home, new BigDecimal("300.00"), BigDecimal.ZERO, null,
            new BigDecimal("300.00"), BudgetStatus.HEALTHY));
        budgetCategories.add(new BudgetResponse.CategoryBudget(food, null, new BigDecimal("80.00"), null, null, null));
        spent(food, 2026, 11, "80.00");
        spent(fun, 2026, 11, "620.00");
        spent(null, 2026, 11, "40.00");
        spent(food, 2026, 1, "15.00");
        // a category removed meanwhile is left out
        spent(UUID.randomUUID(), 2026, 11, "999.00");

        DashboardLayer2Response response = tactical();

        assertEquals("2026-11", response.month());
        assertEquals(List.of(
            new DashboardLayer2Response.CategorySpending(fun, "Lazer", "#111111", new BigDecimal("620.00"),
                new BigDecimal("500.00"), BudgetStatus.OVERSPENT),
            new DashboardLayer2Response.CategorySpending(food, "Mercado", "#111111", new BigDecimal("80.00"), null, null),
            new DashboardLayer2Response.CategorySpending(null, "Não classificado", null, new BigDecimal("40.00"), null,
                null),
            new DashboardLayer2Response.CategorySpending(home, "Casa", "#111111", BigDecimal.ZERO,
                new BigDecimal("300.00"), BudgetStatus.HEALTHY)), response.categories());

        assertEquals(3, response.trends().size());
        DashboardLayer2Response.CategoryTrend foodTrend = response.trends().get(0);
        assertEquals(food, foodTrend.categoryId());
        assertEquals("Mercado", foodTrend.name());
        assertEquals(12, foodTrend.months().size());
        assertEquals(new DashboardLayer2Response.MonthSpent("2025-12", BigDecimal.ZERO), foodTrend.months().get(0));
        assertEquals(new DashboardLayer2Response.MonthSpent("2026-01", new BigDecimal("15.00")),
            foodTrend.months().get(1));
        assertEquals(new DashboardLayer2Response.MonthSpent("2026-11", new BigDecimal("80.00")),
            foodTrend.months().get(11));
        assertEquals("Não classificado", response.trends().get(2).name());
    }

    @Test
    void tacticalSpendingOnlyInThePastStaysInTheTrendsNotInTheMonth() {
        UUID food = child("Mercado");
        spent(food, 2026, 10, "50.00");

        DashboardLayer2Response response = tactical();

        assertEquals(List.of(), response.categories());
        assertEquals(1, response.trends().size());
        assertEquals(new BigDecimal("50.00"), response.trends().get(0).months().get(10).spent());
        assertEquals(BigDecimal.ZERO, response.trends().get(0).months().get(11).spent());
    }

    @Test
    void tacticalWithoutSpendingOrBudgetIsEmpty() {
        DashboardLayer2Response response = tactical();

        assertEquals(new DashboardLayer2Response("2026-11", List.of(), List.of()), response);
    }
}
