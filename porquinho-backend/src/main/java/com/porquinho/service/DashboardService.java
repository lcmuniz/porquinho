package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.FinancialHealthResponse;
import com.porquinho.dto.FinancialHealthResponse.HealthStatus;
import com.porquinho.entity.Goal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Dashboard GPS (Epic 7). Story 7.2: the financial health traffic light. */
@Service
public class DashboardService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final BudgetService budgetService;
    private final AccountService accountService;
    private final GoalService goalService;
    private final Clock clock;

    public DashboardService(BudgetService budgetService, AccountService accountService, GoalService goalService,
                            Clock clock) {
        this.budgetService = budgetService;
        this.accountService = accountService;
        this.goalService = goalService;
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
