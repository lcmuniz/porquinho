package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.entity.Account;
import com.porquinho.entity.Account.AccountType;
import com.porquinho.entity.Category;
import com.porquinho.entity.Goal;
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
 * metrics (category order, goal %, projection on the first, a middle and the last day of the month).
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

    private DashboardService service;
    private UUID userId;
    private final List<Account> accounts = new ArrayList<>();
    private final List<Goal> goals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DashboardService(budgetService, accountService, goalService, categoryService, transactionRepository,
            CLOCK);
        userId = UUID.randomUUID();
        // the overview (7.3) reads the total from the budget, not the accounts
        lenient().when(accountService.listActive(userId)).thenReturn(accounts);
        when(goalService.listActive(userId)).thenReturn(goals);
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
            clock).overview(userId);
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
}
