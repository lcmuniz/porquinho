package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.SaveBudgetRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.BudgetAllocation;
import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.BudgetAllocationRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Monthly budget per child category (Story 6.3). Spent and committed are computed from the transactions (no stored
 * spent_amount) and cached for 30 s by {@link BudgetBalanceCache} (Story 6.4); allocations and total available are read
 * on every call. Total available is the sum of the active accounts' balances (cards left out); going over it
 * is only a warning on the screen, never checked here.
 */
@Service
public class BudgetService {

    public static final String INVALID_CATEGORY_MESSAGE = "Escolha categorias válidas: só categorias ativas, sem grupos";
    public static final String DUPLICATE_CATEGORY_MESSAGE = "Cada categoria pode aparecer só uma vez no orçamento";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal LOW_SHARE = new BigDecimal("0.20");

    private final BudgetAllocationRepository budgetAllocationRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetBalanceCache balanceCache;
    private final Clock clock;

    public BudgetService(BudgetAllocationRepository budgetAllocationRepository, CategoryRepository categoryRepository,
                         TransactionRepository transactionRepository, AccountRepository accountRepository,
                         BudgetBalanceCache balanceCache, Clock clock) {
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.balanceCache = balanceCache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID userId, String month) {
        YearMonth yearMonth = parse(month);
        return build(userId, yearMonth, children(categoryRepository.findActiveByUserId(userId)));
    }

    /** Replaces the month's allocations; 400 (nothing written) on a repeated, group, removed or foreign category. */
    @Transactional
    public BudgetResponse save(UUID userId, String month, List<SaveBudgetRequest.Item> items) {
        YearMonth yearMonth = parse(month);
        // the lock serializes concurrent saves of the same user; the active children also validate the request
        List<Category> children = children(categoryRepository.findActiveByUserIdForUpdate(userId));
        Set<UUID> allowed = new HashSet<>();
        children.forEach(c -> allowed.add(c.getId()));
        Set<UUID> seen = new HashSet<>();
        for (SaveBudgetRequest.Item item : items) {
            if (!allowed.contains(item.categoryId())) {
                throw new BadRequestException(INVALID_CATEGORY_MESSAGE);
            }
            if (!seen.add(item.categoryId())) {
                throw new BadRequestException(DUPLICATE_CATEGORY_MESSAGE);
            }
        }
        LocalDate monthYear = yearMonth.atDay(1);
        if (!allowed.isEmpty()) {
            budgetAllocationRepository.deleteByUserIdAndMonthYearAndCategoryIdIn(userId, monthYear, allowed);
        }
        budgetAllocationRepository.saveAll(items.stream()
            .map(i -> new BudgetAllocation(userId, i.categoryId(), monthYear, i.amount())).toList());
        return build(userId, yearMonth, children(categoryRepository.findActiveByUserId(userId)));
    }

    private BudgetResponse build(UUID userId, YearMonth yearMonth, List<Category> children) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        Map<UUID, BigDecimal> allocated = new HashMap<>();
        for (BudgetAllocation a : budgetAllocationRepository.findByUserIdAndMonthYear(userId, from)) {
            allocated.put(a.getCategoryId(), a.getAllocatedAmount());
        }
        boolean open = !yearMonth.isBefore(YearMonth.now(clock.withZone(SAO_PAULO)));
        BudgetBalanceCache.Sums cached = balanceCache.get(userId, yearMonth, () -> new BudgetBalanceCache.Sums(
            sums(transactionRepository.sumSpentByCategory(userId, from, to)),
            open ? sums(transactionRepository.sumCommittedByCategory(userId, from, to)) : null));
        Map<UUID, BigDecimal> spent = cached.spent();
        Map<UUID, BigDecimal> committed = open ? cached.committed() : null;

        List<BudgetResponse.CategoryBudget> categories = children.stream()
            .map(c -> category(c.getId(), allocated.get(c.getId()), spent.getOrDefault(c.getId(), BigDecimal.ZERO),
                committed == null ? null : committed.getOrDefault(c.getId(), BigDecimal.ZERO)))
            .toList();
        BigDecimal totalAllocated = categories.stream().map(BudgetResponse.CategoryBudget::allocated)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAvailable = accountRepository.findActiveByUserId(userId).stream()
            .map(Account::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetResponse(yearMonth.toString(), totalAvailable, totalAllocated, categories);
    }

    private static BudgetResponse.CategoryBudget category(UUID id, BigDecimal allocated, BigDecimal spent,
                                                          BigDecimal committed) {
        BigDecimal remaining = allocated == null ? null : allocated.subtract(spent);
        return new BudgetResponse.CategoryBudget(id, allocated, spent, committed, remaining, status(allocated, remaining));
    }

    /** The epic's rule: OVERSPENT below 0, LOW up to 20% of the allocated, HEALTHY above; null without allocation. */
    static BudgetStatus status(BigDecimal allocated, BigDecimal remaining) {
        if (remaining == null) {
            return null;
        }
        if (remaining.signum() < 0) {
            return BudgetStatus.OVERSPENT;
        }
        return remaining.compareTo(allocated.multiply(LOW_SHARE)) <= 0 ? BudgetStatus.LOW : BudgetStatus.HEALTHY;
    }

    private static List<Category> children(List<Category> categories) {
        return categories.stream().filter(c -> c.getParentCategoryId() != null).toList();
    }

    private static Map<UUID, BigDecimal> sums(List<Object[]> rows) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.put((UUID) row[0], (BigDecimal) row[1]);
            }
        }
        return result;
    }

    private static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(TransactionService.INVALID_MONTH_MESSAGE);
        }
    }
}
