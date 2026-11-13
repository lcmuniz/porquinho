package com.porquinho.service;

import com.porquinho.dto.DashboardLayer2Response;
import com.porquinho.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 7.5: the monthly spending query against the real schema (PostgreSQL via Testcontainers): one row per category
 * and month, uncategorized included, Epic 6 rule (pending, future installments, credits and card payments out), the
 * 12-month window and another user's rows left out. Not @Transactional; cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardTacticalIntegrationTest {

    private static final YearMonth CURRENT = YearMonth.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private TransactionRepository repository;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private BudgetBalanceCache balanceCache;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DashboardCache dashboardCache;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;
    private UUID account;
    private UUID food;

    @BeforeEach
    void setUp() {
        userId = newUser();
        account = newAccount(userId);
        UUID group = newCategory(userId, "Essenciais", null);
        food = newCategory(userId, "Mercado", group);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            dashboardCache.evictAll(id);
            balanceCache.evict(id);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
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

    private UUID newAccount(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Nubank', 'CHECKING', 0, 0)", id, owner);
        return id;
    }

    private UUID newCategory(UUID owner, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, owner, name, parent);
        return id;
    }

    private void row(UUID owner, UUID accountId, UUID category, YearMonth month, String amount, String type, String kind,
                     String status, boolean future) {
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, 'X', 'X', ?, ?, ?, ?, ?)", UUID.randomUUID(), owner, accountId, category,
            month.atDay(10), new BigDecimal(amount), type, kind, status, future);
    }

    private void spent(UUID category, YearMonth month, String amount) {
        row(userId, account, category, month, amount, "DEBIT", "REGULAR", "CONFIRMED", false);
    }

    @Test
    void oneRowPerCategoryAndMonthWithTheEpic6Rule() {
        spent(food, CURRENT, "100.00");
        spent(food, CURRENT, "20.00");
        spent(food, CURRENT.minusMonths(2), "30.00");
        spent(null, CURRENT, "5.00");
        spent(food, CURRENT.minusMonths(12), "999.00");
        row(userId, account, food, CURRENT, "1.00", "DEBIT", "REGULAR", "PENDING", false);
        row(userId, account, food, CURRENT, "2.00", "DEBIT", "REGULAR", "CONFIRMED", true);
        row(userId, account, food, CURRENT, "3.00", "CREDIT", "REGULAR", "CONFIRMED", false);
        row(userId, account, food, CURRENT, "4.00", "DEBIT", "CARD_PAYMENT", "CONFIRMED", false);
        spent(food, CURRENT, "8.00");
        jdbc.update("UPDATE transactions SET deleted_at = NOW() WHERE user_id = ? AND amount = 8.00", userId);
        UUID other = newUser();
        row(other, newAccount(other), null, CURRENT, "777.00", "DEBIT", "REGULAR", "CONFIRMED", false);

        List<Object[]> rows = repository.sumSpentByCategoryAndMonth(userId, CURRENT.minusMonths(11).atDay(1),
            CURRENT.atEndOfMonth());

        assertThat(rows).extracting(r -> r[0] + "|" + YearMonth.of(((Number) r[1]).intValue(),
                ((Number) r[2]).intValue()) + "|" + ((BigDecimal) r[3]).toPlainString())
            .containsExactlyInAnyOrder(food + "|" + CURRENT + "|120.00", food + "|" + CURRENT.minusMonths(2) + "|30.00",
                "null|" + CURRENT + "|5.00");
    }

    @Test
    void theTacticalViewHasTheMonthAndTwelveMonthsOfTrend() {
        spent(food, CURRENT, "120.00");
        spent(food, CURRENT.minusMonths(11), "30.00");
        spent(null, CURRENT, "5.00");

        DashboardLayer2Response response = dashboardService.tactical(userId);

        assertThat(response.month()).isEqualTo(CURRENT.toString());
        assertThat(response.categories()).extracting(DashboardLayer2Response.CategorySpending::name)
            .containsExactly("Mercado", "Não classificado");
        DashboardLayer2Response.CategoryTrend trend = response.trends().stream()
            .filter(t -> food.equals(t.categoryId())).findFirst().orElseThrow();
        assertThat(trend.months()).hasSize(12);
        assertThat(trend.months().get(0).spent()).isEqualByComparingTo("30.00");
        assertThat(trend.months().get(1).spent()).isEqualByComparingTo("0");
        assertThat(trend.months().get(11).spent()).isEqualByComparingTo("120.00");
    }
}
