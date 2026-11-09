package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.dto.ImportTransactionResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionImportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The rules every write of a transaction shares (retro Epic 5, item 25): the target and its advisory lock, decision D3
 * (what enters the balance), the child category a user may choose, and the pieces of a response.
 */
@Component
public class TransactionCore {

    private static final Logger log = LoggerFactory.getLogger(TransactionCore.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String GROUP_NOT_ALLOWED_MESSAGE = "Escolha uma categoria, não um grupo";

    /** The confidence of a category chosen by the user. */
    static final int USER_CONFIDENCE = 100;

    private final TransactionImportRepository transactionImportRepository;
    private final CategoryRepository categoryRepository;
    private final BalanceHistoryRecorder balanceHistoryRecorder;

    public TransactionCore(TransactionImportRepository transactionImportRepository,
                           CategoryRepository categoryRepository, BalanceHistoryRecorder balanceHistoryRecorder) {
        this.transactionImportRepository = transactionImportRepository;
        this.categoryRepository = categoryRepository;
        this.balanceHistoryRecorder = balanceHistoryRecorder;
    }

    /** The target of a transaction or import: its card, else its account. */
    // same rule as COALESCE(t.creditCardId, t.accountId) in TransactionRepository.findEditableTarget
    static UUID target(UUID cardId, UUID accountId) {
        return cardId != null ? cardId : accountId;
    }

    /** Advisory lock key of a target (account or card id). A collision only serializes two unrelated imports. */
    static long lockKey(UUID targetId) {
        return targetId.getMostSignificantBits() ^ targetId.getLeastSignificantBits();
    }

    static BigDecimal signed(Transaction.Type type, BigDecimal amount) {
        return type == Transaction.Type.CREDIT ? amount : amount.negate();
    }

    /** Takes the advisory lock of each target (nulls ignored), distinct keys in ascending order, so no two deadlock. */
    void lockTargets(UUID... targets) {
        Arrays.stream(targets).filter(Objects::nonNull).map(TransactionCore::lockKey).distinct().sorted()
            .forEach(transactionImportRepository::lockTarget);
    }

    /** Decision D3: a row enters the balance when it is on an account and dated after the account's reference date. */
    boolean inBalance(Account account, LocalDate date) {
        return inBalance(account).test(date);
    }

    /** Decision D3 for many rows of one account: its reference date is read once. */
    Predicate<LocalDate> inBalance(Account account) {
        if (account == null) {
            return date -> false;
        }
        LocalDate reference = balanceHistoryRecorder.referenceDate(account);
        return date -> date.isAfter(reference);
    }

    /** A category of the user, active and not a group: 404 when not available, 400 for a group. */
    Category requireChildCategory(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findActiveByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(CategoryService.NOT_FOUND_MESSAGE));
        if (category.getParentCategoryId() == null) {
            throw new BadRequestException(GROUP_NOT_ALLOWED_MESSAGE);
        }
        return category;
    }

    /** The categories of {@code rows} and their parents, by id, removed ones included. */
    Map<UUID, Category> categoriesOf(UUID userId, List<Transaction> rows) {
        Set<UUID> ids = rows.stream().map(Transaction::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Category> categories = new HashMap<>(categoryRepository.findByUserIdAndIdIn(userId, ids).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity())));
        Set<UUID> parents = new HashSet<>();
        for (Category category : categories.values()) {
            if (category.getParentCategoryId() != null && !categories.containsKey(category.getParentCategoryId())) {
                parents.add(category.getParentCategoryId());
            }
        }
        if (!parents.isEmpty()) {
            categoryRepository.findByUserIdAndIdIn(userId, parents).forEach(parent -> categories.put(parent.getId(), parent));
        }
        return categories;
    }

    static ImportTransactionResponse.Category category(UUID categoryId, Map<UUID, Category> categories) {
        Category category = categoryId == null ? null : categories.get(categoryId);
        if (category == null) {
            return null;
        }
        Category parent = category.getParentCategoryId() == null ? null : categories.get(category.getParentCategoryId());
        return new ImportTransactionResponse.Category(category.getId(), category.getCategoryName(),
            parent == null ? null : parent.getCategoryName());
    }

    /** The stored JSON as an object; never expected to fail (the column is jsonb), and a marker is not worth a 500. */
    static JsonNode pixDetails(Transaction row) {
        if (row.getPixDetails() == null) {
            return null;
        }
        try {
            return JSON.readTree(row.getPixDetails());
        } catch (JsonProcessingException e) {
            log.warn("Transaction {} has unreadable pix_details", row.getId());
            return null;
        }
    }
}
