package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.CategoryBudgetHistoryResponse;
import com.porquinho.dto.ReallocateBudgetRequest;
import com.porquinho.dto.SaveBudgetRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.BudgetAllocation;
import com.porquinho.entity.BudgetReallocation;
import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.BudgetAllocationRepository;
import com.porquinho.repository.BudgetReallocationRepository;
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
import java.util.Locale;
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

    public static final String SAME_CATEGORY_MESSAGE = "Escolha categorias diferentes";
    public static final String CLOSED_MONTH_MESSAGE = "Não dá para mover dinheiro em um mês que já fechou";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal LOW_SHARE = new BigDecimal("0.20");

    private final BudgetAllocationRepository budgetAllocationRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetReallocationRepository budgetReallocationRepository;
    private final BudgetBalanceCache balanceCache;
    private final Clock clock;

    public BudgetService(BudgetAllocationRepository budgetAllocationRepository, CategoryRepository categoryRepository,
                         TransactionRepository transactionRepository, AccountRepository accountRepository,
                         BudgetReallocationRepository budgetReallocationRepository,
                         BudgetBalanceCache balanceCache, Clock clock) {
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.budgetReallocationRepository = budgetReallocationRepository;
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

    /**
     * Moves money between two child categories of the month (Story 6.5) and records it; 400 (nothing written) on a
     * closed month, the same category, an invalid category, a source without allocation, overspent or short.
     * Nothing to invalidate: the cache holds only spent and committed, allocations are read on every call.
     */
    @Transactional
    public BudgetResponse reallocate(UUID userId, String month, ReallocateBudgetRequest request) {
        YearMonth yearMonth = parse(month);
        if (yearMonth.isBefore(YearMonth.now(clock.withZone(SAO_PAULO)))) {
            throw new BadRequestException(CLOSED_MONTH_MESSAGE);
        }
        if (request.fromCategoryId().equals(request.toCategoryId())) {
            throw new BadRequestException(SAME_CATEGORY_MESSAGE);
        }
        // same lock as save: serializes with it and with other reallocations of the user
        List<Category> children = children(categoryRepository.findActiveByUserIdForUpdate(userId));
        Category from = children.stream().filter(c -> c.getId().equals(request.fromCategoryId())).findFirst()
            .orElseThrow(() -> new BadRequestException(INVALID_CATEGORY_MESSAGE));
        if (children.stream().noneMatch(c -> c.getId().equals(request.toCategoryId()))) {
            throw new BadRequestException(INVALID_CATEGORY_MESSAGE);
        }
        LocalDate monthYear = yearMonth.atDay(1);
        Map<UUID, BudgetAllocation> allocations = new HashMap<>();
        for (BudgetAllocation a : budgetAllocationRepository.findByUserIdAndMonthYear(userId, monthYear)) {
            allocations.put(a.getCategoryId(), a);
        }
        BudgetAllocation source = allocations.get(from.getId());
        if (source == null) {
            throw new BadRequestException(from.getCategoryName() + " não tem orçamento neste mês");
        }
        // spent straight from the database, not the 30 s cache
        BigDecimal spent = sums(transactionRepository.sumSpentByCategory(userId, monthYear, yearMonth.atEndOfMonth()))
            .getOrDefault(from.getId(), BigDecimal.ZERO);
        BigDecimal remaining = source.getAllocatedAmount().subtract(spent);
        if (remaining.signum() < 0) {
            throw new BadRequestException(from.getCategoryName() + " está estourada: não dá para mover dinheiro dela");
        }
        if (request.amount().compareTo(remaining) > 0) {
            throw new BadRequestException("O valor passa do restante de " + from.getCategoryName() + " ("
                + brl(remaining) + ")");
        }
        source.setAllocatedAmount(source.getAllocatedAmount().subtract(request.amount()));
        BudgetAllocation target = allocations.get(request.toCategoryId());
        if (target == null) {
            target = new BudgetAllocation(userId, request.toCategoryId(), monthYear, request.amount());
        } else {
            target.setAllocatedAmount(target.getAllocatedAmount().add(request.amount()));
        }
        budgetAllocationRepository.saveAll(List.of(source, target));
        budgetReallocationRepository.save(new BudgetReallocation(userId, from.getId(), request.toCategoryId(),
            monthYear, request.amount()));
        return build(userId, yearMonth, children);
    }

    /**
     * Every month of the child category with an allocation, up to the current one, oldest first (Story 6.8). 404 for a
     * group, removed, foreign or missing category. Read straight from the database (no cache): two queries.
     */
    @Transactional(readOnly = true)
    public CategoryBudgetHistoryResponse history(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findActiveByIdAndUserId(categoryId, userId)
            .filter(c -> c.getParentCategoryId() != null)
            .orElseThrow(() -> new ResourceNotFoundException(CategoryService.NOT_FOUND_MESSAGE));
        YearMonth current = YearMonth.now(clock.withZone(SAO_PAULO));
        List<BudgetAllocation> allocations = budgetAllocationRepository
            .findByUserIdAndCategoryIdAndMonthYearLessThanEqualOrderByMonthYear(userId, categoryId, current.atDay(1));
        Map<YearMonth, BigDecimal> spent = new HashMap<>();
        if (!allocations.isEmpty()) {
            for (Object[] row : transactionRepository.sumSpentByMonth(userId, categoryId,
                allocations.get(0).getMonthYear(), current.atEndOfMonth())) {
                spent.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), (BigDecimal) row[2]);
            }
        }
        List<CategoryBudgetHistoryResponse.Month> months = allocations.stream().map(a -> {
            YearMonth month = YearMonth.from(a.getMonthYear());
            BigDecimal monthSpent = spent.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal remaining = a.getAllocatedAmount().subtract(monthSpent);
            return new CategoryBudgetHistoryResponse.Month(month.toString(), a.getAllocatedAmount(), monthSpent,
                remaining, status(a.getAllocatedAmount(), remaining));
        }).toList();
        return new CategoryBudgetHistoryResponse(category.getId(), category.getCategoryName(), months);
    }

    private static String brl(BigDecimal amount) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", amount);
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
