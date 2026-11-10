package com.porquinho.service;

import com.porquinho.dto.BudgetResponse;
import com.porquinho.dto.BudgetStatus;
import com.porquinho.dto.SaveBudgetRequest;
import com.porquinho.dto.TransactionCreateRequest;
import com.porquinho.dto.TransactionUpdateRequest;
import com.porquinho.entity.BudgetAllocation;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.BudgetAllocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 6.3: monthly budget per child category against the real schema (PostgreSQL via Testcontainers, Flyway through
 * V32). Months are relative to the current one in São Paulo, like the service. Story 6.4: status and the 30 s Redis cache
 * (real Redis; rows inserted by jdbc skip the invalidation, so {@link #row} evicts by hand).
 */
@SpringBootTest
@ActiveProfiles("test")
class BudgetIntegrationTest {

    private static final YearMonth CURRENT = YearMonth.now(ZoneId.of("America/Sao_Paulo"));
    private static final YearMonth PAST = CURRENT.minusMonths(1);

    @Autowired
    private BudgetService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BudgetAllocationRepository repository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private BudgetBalanceCache balanceCache;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private TransactionImportReviewService importReviewService;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;
    private UUID group;
    private UUID food;
    private UUID transport;

    @BeforeEach
    void setUp() {
        userId = newUser();
        group = newCategory(userId, "Essenciais", null);
        food = newCategory(userId, "Alimentação", group);
        transport = newCategory(userId, "Transporte", group);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            balanceCache.evict(id);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM budget_allocations WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID newCategory(UUID owner, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, owner, name, parent);
        return id;
    }

    private UUID newAccount(String balance, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance, "
            + "deleted_at) VALUES (?, ?, 'Nubank', 'CHECKING', ?, ?, " + (deleted ? "NOW()" : "NULL") + ")", id, userId,
            new BigDecimal(balance), new BigDecimal(balance));
        return id;
    }

    private UUID newCard(boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO credit_cards (id, user_id, card_name, card_limit, due_day, deleted_at) "
            + "VALUES (?, ?, 'Nubank', 1000, 10, " + (deleted ? "NOW()" : "NULL") + ")", id, userId);
        return id;
    }

    private void row(UUID accountId, UUID cardId, UUID category, YearMonth month, String amount, String type,
                     String kind, String status, boolean future) {
        rowWithoutEvict(accountId, cardId, category, month, amount, type, kind, status, future);
        balanceCache.evict(userId);
    }

    private void rowWithoutEvict(UUID accountId, UUID cardId, UUID category, YearMonth month, String amount, String type,
                                 String kind, String status, boolean future) {
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, credit_card_id, category_id, transaction_date, "
            + "description, original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'X', 'X', ?, ?, ?, ?, ?)", UUID.randomUUID(), userId, accountId, cardId, category,
            month.atDay(10), new BigDecimal(amount), type, kind, status, future);
    }

    private static SaveBudgetRequest.Item item(UUID category, String amount) {
        return new SaveBudgetRequest.Item(category, new BigDecimal(amount));
    }

    private static BudgetResponse.CategoryBudget of(BudgetResponse response, UUID category) {
        return response.categories().stream().filter(c -> c.categoryId().equals(category)).findFirst().orElseThrow();
    }

    private int allocations() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM budget_allocations WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void saveReturnsTheMonthRecalculatedAndGetReadsItBack() {
        newAccount("1000.00", false);
        newAccount("500.00", false);
        newAccount("9999.00", true);

        BudgetResponse saved = service.save(userId, CURRENT.toString(), List.of(item(food, "800.00"), item(transport, "0")));

        assertThat(saved.month()).isEqualTo(CURRENT.toString());
        assertThat(saved.totalAvailable()).isEqualByComparingTo("1500.00");
        assertThat(saved.totalAllocated()).isEqualByComparingTo("800.00");
        assertThat(saved.categories()).extracting(BudgetResponse.CategoryBudget::categoryId)
            .containsExactly(food, transport);  // children only, by name
        assertThat(of(saved, transport).allocated()).isEqualByComparingTo("0");

        BudgetResponse read = service.get(userId, CURRENT.toString());
        assertThat(of(read, food).allocated()).isEqualByComparingTo("800.00");
        assertThat(read.totalAllocated()).isEqualByComparingTo("800.00");
    }

    @Test
    void putReplacesTheSetAndALeftOutCategoryLosesItsRow() {
        service.save(userId, CURRENT.toString(), List.of(item(food, "800"), item(transport, "200")));

        BudgetResponse saved = service.save(userId, CURRENT.toString(), List.of(item(food, "700")));

        assertThat(of(saved, food).allocated()).isEqualByComparingTo("700");
        assertThat(of(saved, transport).allocated()).isNull();
        assertThat(allocations()).isEqualTo(1);
    }

    @Test
    void aMonthWithoutAllocationsHasEveryChildWithNullAndTheSpentComputed() {
        UUID account = newAccount("100", false);
        row(account, null, food, CURRENT.plusMonths(1), "30.00", "DEBIT", "REGULAR", "CONFIRMED", false);

        BudgetResponse read = service.get(userId, CURRENT.plusMonths(1).toString());

        assertThat(read.categories()).allSatisfy(c -> assertThat(c.allocated()).isNull());
        assertThat(of(read, food).spent()).isEqualByComparingTo("30.00");
        assertThat(of(read, transport).spent()).isEqualByComparingTo("0");
        assertThat(read.totalAllocated()).isEqualByComparingTo("0");
    }

    @Test
    void onlyConfirmedRegularDebitsOfTheMonthAreSpent() {
        UUID account = newAccount("100", false);
        UUID card = newCard(false);
        row(account, null, food, CURRENT, "50.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        row(account, null, food, CURRENT, "20.00", "CREDIT", "REGULAR", "CONFIRMED", false);
        row(account, null, food, CURRENT, "300.00", "DEBIT", "CARD_PAYMENT", "CONFIRMED", false);
        row(account, null, food, CURRENT, "40.00", "DEBIT", "REGULAR", "PENDING", false);
        row(null, card, food, CURRENT, "60.00", "DEBIT", "REGULAR", "CONFIRMED", true);
        row(account, null, food, PAST, "70.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, review_status, deleted_at) VALUES (?, ?, ?, ?, ?, 'X', 'X', "
            + "80, 'DEBIT', 'CONFIRMED', NOW())", UUID.randomUUID(), userId, account, food, CURRENT.atDay(5));

        assertThat(of(service.get(userId, CURRENT.toString()), food).spent()).isEqualByComparingTo("50.00");
    }

    @Test
    void committedSumsActiveFutureInstallmentsOfTheMonthOnlyFromTheCurrentMonthOn() {
        UUID card = newCard(false);
        UUID removedCard = newCard(true);
        row(null, card, food, CURRENT, "100.00", "DEBIT", "REGULAR", "CONFIRMED", true);
        row(null, removedCard, food, CURRENT, "999.00", "DEBIT", "REGULAR", "CONFIRMED", true);
        row(null, card, food, CURRENT.plusMonths(1), "25.00", "DEBIT", "REGULAR", "CONFIRMED", true);
        row(null, card, food, PAST, "100.00", "DEBIT", "REGULAR", "CONFIRMED", true);
        row(null, card, food, CURRENT, "77.00", "DEBIT", "REGULAR", "PENDING", true);  // import still in review

        BudgetResponse current = service.get(userId, CURRENT.toString());
        assertThat(of(current, food).committed()).isEqualByComparingTo("100.00");
        assertThat(of(current, transport).committed()).isEqualByComparingTo("0");
        assertThat(of(service.get(userId, CURRENT.plusMonths(1).toString()), food).committed())
            .isEqualByComparingTo("25.00");
        assertThat(service.get(userId, PAST.toString()).categories())
            .allSatisfy(c -> assertThat(c.committed()).isNull());
    }

    @Test
    void aClosedMonthCanBeEdited() {
        BudgetResponse saved = service.save(userId, PAST.toString(), List.of(item(food, "10")));
        assertThat(of(saved, food).allocated()).isEqualByComparingTo("10");
        assertThat(of(saved, food).committed()).isNull();
    }

    @Test
    void anAllocationOfACategoryRemovedSinceSurvivesALaterSaveOfTheMonth() {
        service.save(userId, CURRENT.toString(), List.of(item(food, "800"), item(transport, "200")));
        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE id = ?", transport);

        service.save(userId, CURRENT.toString(), List.of(item(food, "700")));

        assertThat(jdbc.queryForObject("SELECT allocated_amount FROM budget_allocations WHERE category_id = ?",
            BigDecimal.class, transport)).isEqualByComparingTo("200");
        assertThat(allocations()).isEqualTo(2);
    }

    @Test
    void exportReadsTheRowsInOrderAndThePurgeDeletesThemSoTheCategoriesCanGo() {
        // Purge SQL: other users keep theirs (the call order is in AccountDeletionServiceTest)
        service.save(userId, PAST.toString(), List.of(item(food, "10")));
        service.save(userId, CURRENT.toString(), List.of(item(transport, "20")));
        UUID other = newUser();
        UUID otherGroup = newCategory(other, "G", null);
        UUID otherChild = newCategory(other, "Filha", otherGroup);
        service.save(other, CURRENT.toString(), List.of(item(otherChild, "5")));

        assertThat(repository.findByUserIdOrdered(userId)).extracting(BudgetAllocation::getCategoryId)
            .containsExactly(food, transport);
        // the real purge runs inside AccountDeletionService's transaction
        Integer purged = transactionTemplate.execute(status -> repository.deleteByUserId(userId));
        assertThat(purged).isEqualTo(2);
        assertThat(allocations()).isZero();
        assertThat(jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", userId))
            .isEqualTo(2);
        assertThat(jdbc.update("DELETE FROM categories WHERE user_id = ?", userId)).isEqualTo(1);
        assertThat(repository.findByUserIdOrdered(other)).hasSize(1);
    }

    @Test
    void anInvalidMonthIs400() {
        assertThatThrownBy(() -> service.get(userId, "2026-13")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.save(userId, "nov", List.of())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void anInvalidCategoryIs400WithoutWritingAnything() {
        service.save(userId, CURRENT.toString(), List.of(item(food, "800")));
        UUID other = newUser();
        UUID otherGroup = newCategory(other, "G", null);
        UUID foreign = newCategory(other, "Outra", otherGroup);
        UUID removed = newCategory(userId, "Removida", group);
        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE id = ?", removed);

        for (List<SaveBudgetRequest.Item> items : List.of(
            List.of(item(transport, "1"), item(group, "10")),
            List.of(item(transport, "1"), item(removed, "10")),
            List.of(item(transport, "1"), item(foreign, "10")),
            List.of(item(transport, "1"), item(transport, "10")))) {
            assertThatThrownBy(() -> service.save(userId, CURRENT.toString(), items))
                .isInstanceOf(BadRequestException.class);
        }

        assertThat(of(service.get(userId, CURRENT.toString()), food).allocated()).isEqualByComparingTo("800");
        assertThat(allocations()).isEqualTo(1);
    }

    @Test
    void usersAreIsolated() {
        service.save(userId, CURRENT.toString(), List.of(item(food, "800")));
        UUID account = newAccount("100", false);
        row(account, null, food, CURRENT, "50.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        UUID other = newUser();
        UUID otherGroup = newCategory(other, "G", null);
        UUID otherChild = newCategory(other, "Filha", otherGroup);

        BudgetResponse read = service.get(other, CURRENT.toString());

        assertThat(read.categories()).extracting(BudgetResponse.CategoryBudget::categoryId).containsExactly(otherChild);
        assertThat(read.categories().get(0).spent()).isEqualByComparingTo("0");
        assertThat(read.totalAllocated()).isEqualByComparingTo("0");
        assertThat(read.totalAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void statusFollowsTheEpicRuleAndIsNullWithoutAllocation() {
        UUID account = newAccount("1000", false);
        UUID bills = newCategory(userId, "Contas", group);
        UUID fun = newCategory(userId, "Lazer", group);
        UUID home = newCategory(userId, "Moradia", group);
        row(account, null, food, CURRENT, "50.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        row(account, null, transport, CURRENT, "80.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        row(account, null, bills, CURRENT, "100.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        row(account, null, fun, CURRENT, "130.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        service.save(userId, CURRENT.toString(), List.of(item(food, "100"), item(transport, "100"), item(bills, "100"),
            item(fun, "100")));

        BudgetResponse read = service.get(userId, CURRENT.toString());

        assertThat(of(read, food).remaining()).isEqualByComparingTo("50");
        assertThat(of(read, food).status()).isEqualTo(BudgetStatus.HEALTHY);
        assertThat(of(read, transport).status()).isEqualTo(BudgetStatus.LOW);
        assertThat(of(read, bills).remaining()).isEqualByComparingTo("0");
        assertThat(of(read, bills).status()).isEqualTo(BudgetStatus.LOW);
        assertThat(of(read, fun).remaining()).isEqualByComparingTo("-30");
        assertThat(of(read, fun).status()).isEqualTo(BudgetStatus.OVERSPENT);
        assertThat(of(read, home).remaining()).isNull();
        assertThat(of(read, home).status()).isNull();
    }

    @Test
    void aWarmCacheDoesNotReadTheTransactionsAgain() {
        UUID account = newAccount("1000", false);
        row(account, null, food, CURRENT, "50.00", "DEBIT", "REGULAR", "CONFIRMED", false);
        assertThat(of(service.get(userId, CURRENT.toString()), food).spent()).isEqualByComparingTo("50.00");

        rowWithoutEvict(account, null, food, CURRENT, "25.00", "DEBIT", "REGULAR", "CONFIRMED", false);

        assertThat(of(service.get(userId, CURRENT.toString()), food).spent()).isEqualByComparingTo("50.00");
        balanceCache.evict(userId);
        assertThat(of(service.get(userId, CURRENT.toString()), food).spent()).isEqualByComparingTo("75.00");
    }

    @Test
    void creatingEditingAndDeletingATransactionInvalidatesTheCache() {
        UUID account = newAccount("1000", false);
        service.save(userId, CURRENT.toString(), List.of(item(food, "100")));
        LocalDate date = CURRENT.atDay(10);
        assertThat(of(service.get(userId, CURRENT.toString()), food).status()).isEqualTo(BudgetStatus.HEALTHY);

        UUID id = transactionService.create(userId, new TransactionCreateRequest(date, "Feira", new BigDecimal("130.00"),
            "DEBIT", food, account, null), "127.0.0.1").id();
        BudgetResponse.CategoryBudget created = of(service.get(userId, CURRENT.toString()), food);
        assertThat(created.spent()).isEqualByComparingTo("130.00");
        assertThat(created.status()).isEqualTo(BudgetStatus.OVERSPENT);

        transactionService.update(userId, id, new TransactionUpdateRequest(date, "Feira", new BigDecimal("90.00"),
            "DEBIT", food, account, null), "127.0.0.1");
        BudgetResponse.CategoryBudget updated = of(service.get(userId, CURRENT.toString()), food);
        assertThat(updated.spent()).isEqualByComparingTo("90.00");
        assertThat(updated.status()).isEqualTo(BudgetStatus.LOW);

        transactionService.delete(userId, id, "127.0.0.1");
        BudgetResponse.CategoryBudget deleted = of(service.get(userId, CURRENT.toString()), food);
        assertThat(deleted.spent()).isEqualByComparingTo("0");
        assertThat(deleted.status()).isEqualTo(BudgetStatus.HEALTHY);
    }

    @Test
    void completingAnImportInvalidatesTheCache() {
        UUID card = newCard(false);
        assertThat(of(service.get(userId, CURRENT.toString()), food).spent()).isEqualByComparingTo("0");
        UUID importId = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, credit_card_id, file_name, file_type, file_size_kb, "
            + "status) VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, 'REVIEW')", importId, userId, card);
        jdbc.update("INSERT INTO transactions (id, user_id, credit_card_id, import_id, category_id, transaction_date, "
            + "description, original_description, amount, transaction_type, review_status) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'X', 'X', 40.00, 'DEBIT', 'APPROVED')", UUID.randomUUID(), userId, card, importId,
            food, CURRENT.atDay(10));

        importReviewService.complete(userId, importId, "127.0.0.1");

        assertThat(of(service.get(userId, CURRENT.toString()), food).spent()).isEqualByComparingTo("40.00");
    }
}
