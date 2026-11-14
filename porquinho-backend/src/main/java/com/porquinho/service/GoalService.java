package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.dto.GoalHistoryResponse;
import com.porquinho.dto.GoalProgressResponse;
import com.porquinho.dto.UpdateGoalAllocationsRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.Goal;
import com.porquinho.entity.GoalAllocation;
import com.porquinho.entity.GoalProgressHistory;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.GoalAllocationRepository;
import com.porquinho.repository.GoalProgressHistoryRepository;
import com.porquinho.repository.GoalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private static final Logger log = LoggerFactory.getLogger(GoalService.class);

    static final String NOT_FOUND_MESSAGE = "Meta não encontrada";
    static final String COMPLETED_MESSAGE = "Esta meta já foi concluída. Reabra a meta para alterá-la";
    static final String NOT_COMPLETED_MESSAGE = "Esta meta não está concluída";
    public static final String AUDIT_COMPLETED = "goal_completed";
    public static final String AUDIT_REOPENED = "goal_reopened";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final GoalRepository goalRepository;
    private final GoalAllocationRepository goalAllocationRepository;
    private final GoalProgressHistoryRepository historyRepository;
    private final AccountRepository accountRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, GoalAllocationRepository goalAllocationRepository,
                       GoalProgressHistoryRepository historyRepository, AccountRepository accountRepository,
                       AuditLogService auditLogService, Clock clock) {
        this.goalRepository = goalRepository;
        this.goalAllocationRepository = goalAllocationRepository;
        this.historyRepository = historyRepository;
        this.accountRepository = accountRepository;
        this.auditLogService = auditLogService;
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
        goal.setTemplate(request.template());
        Goal saved = goalRepository.save(goal);
        snapshot(saved, null);  // Story 8.8: first history entry, without contribution
        return saved;
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
        requireNotCompleted(goal);
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
     * Story 8.7: 409 on a completed goal; reaching the target completes the goal ("auto":true in the audit).
     */
    @Transactional
    public Goal contribute(UUID userId, UUID goalId, BigDecimal amount, String ipAddress) {
        Goal goal = lockedGoal(userId, goalId);
        requireNotCompleted(goal);
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        goal.setCurrentAmount(goal.getCurrentAmount().add(value));
        GoalAllocation row = monthRow(goal);
        row.setContributedAmount(row.getContributedAmount().add(value));
        goalAllocationRepository.save(row);
        snapshot(goal, value);  // Story 8.8: same transaction as the contribution
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            markCompleted(goal, true, ipAddress);
        }
        return goalRepository.save(goal);
    }

    /**
     * Story 8.7: manual completion of an active goal, even below the target. The monthly allocation stays stored
     * (it comes back on reopen) but completed goals leave the totals. 409 if already completed.
     */
    @Transactional
    public Goal complete(UUID userId, UUID goalId, String ipAddress) {
        Goal goal = lockedGoal(userId, goalId);
        requireNotCompleted(goal);
        markCompleted(goal, false, ipAddress);
        return goalRepository.save(goal);
    }

    /** Story 8.7: back to the active goals with its previous allocation. 409 if the goal is not completed. */
    @Transactional
    public Goal reopen(UUID userId, UUID goalId, String ipAddress) {
        Goal goal = lockedGoal(userId, goalId);
        if (!goal.isCompleted()) throw new ConflictException(NOT_COMPLETED_MESSAGE);
        goal.setCompleted(false);
        goal.setCompletedAt(null);
        auditLogService.log(AUDIT_REOPENED, userId, ipAddress, "{\"id\":\"" + goalId + "\"}");
        return goalRepository.save(goal);
    }

    private void markCompleted(Goal goal, boolean auto, String ipAddress) {
        goal.setCompleted(true);
        goal.setCompletedAt(OffsetDateTime.now(clock));
        auditLogService.log(AUDIT_COMPLETED, goal.getUserId(), ipAddress,
            "{\"id\":\"" + goal.getId() + "\",\"auto\":" + auto + "}");
    }

    private static void requireNotCompleted(Goal goal) {
        if (goal.isCompleted()) throw new ConflictException(COMPLETED_MESSAGE);
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

    /**
     * Story 8.8: the goal's history (creation, contributions and monthly snapshots), completed goals included.
     * 404 for another user's, removed or missing goal.
     */
    @Transactional(readOnly = true)
    public GoalHistoryResponse history(UUID userId, UUID goalId) {
        Goal goal = goalRepository.findActiveByIdAndUserId(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        List<GoalHistoryResponse.Entry> entries = historyRepository.findByUserIdAndGoalIdOrdered(userId, goalId).stream()
            .map(h -> new GoalHistoryResponse.Entry(h.getSnapshotDate(), h.getContributionAmount(), h.getAmountSnapshot()))
            .toList();
        return new GoalHistoryResponse(goal.getGoalName(), goal.getTargetAmount(), goal.getCreatedAt(),
            goal.getCompletedAt(), entries);
    }

    /**
     * Story 8.8: day 1 at 00:05 (America/Sao_Paulo), one snapshot without contribution per active, not completed goal.
     * Idempotent: skips a goal that already has one on that date. Intentionally NOT @Transactional: each save runs in
     * its own transaction, so one failure does not stop the others (single instance, no ShedLock).
     *
     * @return how many snapshots were written
     */
    @Scheduled(cron = "0 5 0 1 * *", zone = "America/Sao_Paulo")
    public int takeMonthlySnapshots() {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        int written = 0;
        for (Goal goal : goalRepository.findByDeletedAtIsNullAndCompletedFalse()) {
            try {
                if (!historyRepository.existsByUserIdAndGoalIdAndSnapshotDateAndContributionAmountIsNull(
                        goal.getUserId(), goal.getId(), today)) {
                    snapshot(goal, null);
                    written++;
                }
            } catch (RuntimeException e) {
                log.warn("Goal monthly snapshot failed for goal {}: {} - {}", goal.getId(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        log.info("Goal monthly snapshot job done: {} snapshots", written);
        return written;
    }

    /** Story 8.8: the goal's current amount today (America/Sao_Paulo); contribution null when not a contribution. */
    private void snapshot(Goal goal, BigDecimal contribution) {
        historyRepository.save(new GoalProgressHistory(goal.getUserId(), goal.getId(), goal.getCurrentAmount(),
            LocalDate.now(clock.withZone(SAO_PAULO)), contribution));
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
