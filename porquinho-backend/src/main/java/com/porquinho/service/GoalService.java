package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.dto.GoalProgressResponse;
import com.porquinho.dto.UpdateGoalAllocationsRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.Goal;
import com.porquinho.entity.GoalAllocation;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.GoalAllocationRepository;
import com.porquinho.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Financial goals (Story 3.8: create and list; Story 8.2: monthly allocation and contributions).
 * The user id always comes from the JWT; another user's goal is never returned.
 */
@Service
public class GoalService {

    static final String NOT_FOUND_MESSAGE = "Meta não encontrada";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final GoalRepository goalRepository;
    private final GoalAllocationRepository goalAllocationRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, GoalAllocationRepository goalAllocationRepository,
                       AccountRepository accountRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.goalAllocationRepository = goalAllocationRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    /**
     * Creates the goal with current_amount = 0. The controller already validated at most 2 decimals; HALF_UP
     * (instead of UNNECESSARY) keeps another caller from failing with an ArithmeticException.
     */
    @Transactional
    public Goal create(UUID userId, CreateGoalRequest request) {
        Goal goal = new Goal(userId, request.name(),
            request.targetAmount().setScale(2, RoundingMode.HALF_UP),
            request.targetDate(),
            request.monthlyAllocation() == null ? null
                : request.monthlyAllocation().setScale(2, RoundingMode.HALF_UP));
        goal.setDescription(request.description());
        return goalRepository.save(goal);
    }

    /** The user's active goals, oldest first. */
    @Transactional(readOnly = true)
    public List<Goal> listActive(UUID userId) {
        return goalRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countActive(UUID userId) {
        return goalRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    /**
     * Sets the goal's monthly allocation and the current month's allocated_amount (upsert). The contributed amount
     * of the month is kept. 404 for another user's, removed or missing goal.
     */
    @Transactional
    public Goal updateAllocation(UUID userId, UUID goalId, BigDecimal monthlyAllocation) {
        return applyAllocation(lockedGoal(userId, goalId), monthlyAllocation);
    }

    /**
     * Story 8.5: the same rule as {@link #updateAllocation} for several goals, all or nothing. Every goal is locked
     * (in id order, so two batches never deadlock) before anything is written; one missing goal is a 404 and
     * nothing changes. A repeated goal id is a 400. Returns the goals in the request order.
     */
    @Transactional
    public List<Goal> updateAllocations(UUID userId, List<UpdateGoalAllocationsRequest.Item> items) {
        if (items.isEmpty()) throw new BadRequestException("Informe ao menos uma meta");
        if (new HashSet<>(items.stream().map(UpdateGoalAllocationsRequest.Item::goalId).toList()).size() != items.size()) {
            throw new BadRequestException("A mesma meta foi informada mais de uma vez");
        }
        Map<UUID, Goal> locked = new HashMap<>();
        items.stream().map(UpdateGoalAllocationsRequest.Item::goalId).sorted(Comparator.naturalOrder())
            .forEach(id -> locked.put(id, lockedGoal(userId, id)));
        return items.stream().map(item -> applyAllocation(locked.get(item.goalId()), item.monthlyAllocation())).toList();
    }

    private Goal applyAllocation(Goal goal, BigDecimal monthlyAllocation) {
        BigDecimal amount = monthlyAllocation.setScale(2, RoundingMode.HALF_UP);
        goal.setMonthlyAllocation(amount);
        GoalAllocation row = monthRow(goal);
        row.setAllocatedAmount(amount);
        goalAllocationRepository.save(row);
        return goalRepository.save(goal);
    }

    /**
     * Adds the contribution to the goal's current_amount and to the month's contributed_amount. A month without a
     * row gets one with the goal's current allocation (0 when null). No transaction nor account balance changes.
     */
    @Transactional
    public Goal contribute(UUID userId, UUID goalId, BigDecimal amount) {
        Goal goal = lockedGoal(userId, goalId);
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        goal.setCurrentAmount(goal.getCurrentAmount().add(value));
        GoalAllocation row = monthRow(goal);
        row.setContributedAmount(row.getContributedAmount().add(value));
        goalAllocationRepository.save(row);
        return goalRepository.save(goal);
    }

    /** Sum of the active accounts' balances (cards excluded), the same totalAvailable as the budget page. */
    @Transactional(readOnly = true)
    public BigDecimal totalAvailable(UUID userId) {
        return accountRepository.findActiveByUserId(userId).stream()
            .map(Account::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Story 8.3: one point per month (America/Sao_Paulo) from the creation month to the current one. Each point is
     * base + contributions up to that month, with base = currentAmount - all contributions (value from before 8.2),
     * so the last point always equals currentAmount. Computed on read; no snapshot table.
     */
    @Transactional(readOnly = true)
    public GoalProgressResponse progress(UUID userId, UUID goalId) {
        Goal goal = goalRepository.findActiveByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        List<GoalAllocation> rows = goalAllocationRepository.findByUserIdAndGoalIdOrderByMonthYearAsc(userId, goalId);
        BigDecimal amount = rows.stream().map(GoalAllocation::getContributedAmount)
            .reduce(goal.getCurrentAmount(), BigDecimal::subtract);
        YearMonth current = YearMonth.now(clock.withZone(SAO_PAULO));
        YearMonth start = YearMonth.from(goal.getCreatedAt().atZoneSameInstant(SAO_PAULO));
        if (start.isAfter(current)) start = current;
        List<GoalProgressResponse.Point> points = new ArrayList<>();
        int next = 0;
        for (YearMonth month = start; !month.isAfter(current); month = month.plusMonths(1)) {
            // Rows before the creation month (should not exist) fold into the first point
            while (next < rows.size() && !YearMonth.from(rows.get(next).getMonthYear()).isAfter(month)) {
                amount = amount.add(rows.get(next++).getContributedAmount());
            }
            points.add(new GoalProgressResponse.Point(month.toString(), amount));
        }
        return new GoalProgressResponse(points);
    }

    private Goal lockedGoal(UUID userId, UUID goalId) {
        return goalRepository.findActiveByIdAndUserIdForUpdate(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /** The goal's row of the current month in America/Sao_Paulo, or a new one with the goal's allocation. */
    private GoalAllocation monthRow(Goal goal) {
        LocalDate month = YearMonth.now(clock.withZone(SAO_PAULO)).atDay(1);
        return goalAllocationRepository.findByUserIdAndGoalIdAndMonthYear(goal.getUserId(), goal.getId(), month)
            .orElseGet(() -> new GoalAllocation(goal.getUserId(), goal.getId(), month,
                goal.getMonthlyAllocation() == null ? BigDecimal.ZERO.setScale(2) : goal.getMonthlyAllocation()));
    }
}
