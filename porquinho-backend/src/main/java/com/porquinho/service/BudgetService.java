package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
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
 * Monthly budget per child category (Story 6.3). Spent and committed are computed from the transactions on every read
 * (no stored spent_amount). Total available is the sum of the active accounts' balances (cards left out); going over it
 * is only a warning on the screen, never checked here.
 */
@Service
public class BudgetService {

    public static final String INVALID_CATEGORY_MESSAGE = "Escolha categorias válidas: só categorias ativas, sem grupos";
    public static final String DUPLICATE_CATEGORY_MESSAGE = "Cada categoria pode aparecer só uma vez no orçamento";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final BudgetAllocationRepository budgetAllocationRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public BudgetService(BudgetAllocationRepository budgetAllocationRepository, CategoryRepository categoryRepository,
                         TransactionRepository transactionRepository, AccountRepository accountRepository, Clock clock) {
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
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
        Map<UUID, BigDecimal> spent = sums(transactionRepository.sumSpentByCategory(userId, from, to));
        boolean open = !yearMonth.isBefore(YearMonth.now(clock.withZone(SAO_PAULO)));
        Map<UUID, BigDecimal> committed = open ? sums(transactionRepository.sumCommittedByCategory(userId, from, to)) : null;

        List<BudgetResponse.CategoryBudget> categories = children.stream()
            .map(c -> new BudgetResponse.CategoryBudget(c.getId(), allocated.get(c.getId()),
                spent.getOrDefault(c.getId(), BigDecimal.ZERO),
                committed == null ? null : committed.getOrDefault(c.getId(), BigDecimal.ZERO)))
            .toList();
        BigDecimal totalAllocated = categories.stream().map(BudgetResponse.CategoryBudget::allocated)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAvailable = accountRepository.findActiveByUserId(userId).stream()
            .map(Account::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetResponse(yearMonth.toString(), totalAvailable, totalAllocated, categories);
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
