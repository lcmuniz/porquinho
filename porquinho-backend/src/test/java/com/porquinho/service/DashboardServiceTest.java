package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.entity.Account;
import com.porquinho.entity.Account.AccountType;
import com.porquinho.entity.Goal;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 7.2: the traffic light rule, one line of the spec's matrix per test plus the 80/50 limits. */
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

    private DashboardService service;
    private UUID userId;
    private final List<Account> accounts = new ArrayList<>();
    private final List<Goal> goals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DashboardService(budgetService, accountService, goalService, CLOCK);
        userId = UUID.randomUUID();
        when(accountService.listActive(userId)).thenReturn(accounts);
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
}
