package com.porquinho.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retro Epic 7 A2 (R12, NFR1, NFR9): the layers 1 and 2 over 5 years of history of a heavy user, against the real
 * schema (PostgreSQL via Testcontainers) and without cache. A regression guard: at this volume a local PostgreSQL may
 * pass even without the indexes; the proof of the indexes is the EXPLAIN in the spec. Not @Transactional.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardPerformanceIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final int MONTHS = 60;
    private static final int PER_MONTH = 300;

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private DashboardCache dashboardCache;
    @Autowired
    private BudgetBalanceCache balanceCache;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        dashboardCache.evictAll(userId);
        balanceCache.evict(userId);
        jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM budget_allocations WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void theDashboardIndexesReplaceTheUserOnlyOnes() {
        List<String> names = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename IN "
            + "('transactions', 'account_balance_history')", String.class);

        assertThat(names).contains("idx_transactions_user_date", "idx_account_balance_history_user_date")
            .doesNotContain("idx_transactions_user_id", "idx_account_balance_history_user_id");
    }

    @Test
    void fiveYearsOfHistoryAnswerWithinTheNfr() {
        seedFiveYears();

        long overview = bestOf3(() -> dashboardService.overview(userId));
        long tactical = bestOf3(() -> dashboardService.tactical(userId));
        System.out.printf("NFR1/NFR9 5 anos: overview %d ms, tactical %d ms%n", overview, tactical);

        assertThat(overview).isLessThan(2000);
        assertThat(tactical).isLessThan(3000);
    }

    // best of 3 runs, caches evicted before each one: one slow run on a busy CI runner is noise, not the query
    private long bestOf3(Supplier<?> call) {
        call.get(); // warm-up
        long millis = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            dashboardCache.evictAll(userId);
            balanceCache.evict(userId);
            long start = System.nanoTime();
            assertThat(call.get()).isNotNull();
            millis = Math.min(millis, (System.nanoTime() - start) / 1_000_000);
        }
        return millis;
    }

    private void seedFiveYears() {
        List<UUID> accounts = new ArrayList<>();
        for (String name : List.of("Nubank", "Inter", "Itaú")) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, "
                + "current_balance) VALUES (?, ?, ?, 'CHECKING', 0, 0)", id, userId, name);
            accounts.add(id);
        }
        UUID group = newCategory("Essenciais", null);
        List<UUID> categories = new ArrayList<>();
        for (String name : List.of("Mercado", "Lazer", "Transporte", "Casa", "Saúde")) {
            categories.add(newCategory(name, group));
        }
        YearMonth current = YearMonth.now(SAO_PAULO);
        for (UUID category : categories) {
            jdbc.update("INSERT INTO budget_allocations (user_id, category_id, month_year, allocated_amount) "
                + "VALUES (?, ?, ?, 1000)", userId, category, current.atDay(1));
        }

        List<Object[]> transactions = new ArrayList<>();
        List<Object[]> snapshots = new ArrayList<>();
        BigDecimal[] balances = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (int m = MONTHS - 1; m >= 0; m--) {
            YearMonth month = current.minusMonths(m);
            for (int i = 0; i < PER_MONTH; i++) {
                LocalDate date = month.atDay(1 + i % month.lengthOfMonth());
                int a = i % 3;
                BigDecimal amount = BigDecimal.valueOf(i % 50 + 1, 0);
                balances[a] = balances[a].subtract(amount);
                transactions.add(new Object[] {UUID.randomUUID(), userId, accounts.get(a),
                    categories.get(i % categories.size()), date, "Compra " + i, "Compra " + i, amount});
                OffsetDateTime at = date.atTime(12, 0).atOffset(ZoneOffset.UTC).plusSeconds(i);
                snapshots.add(new Object[] {accounts.get(a), userId, balances[a], at, at});
            }
        }
        jdbc.batchUpdate("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEBIT', 'REGULAR', 'CONFIRMED', FALSE)", transactions);
        jdbc.batchUpdate("INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, "
            + "created_by_event, created_at) VALUES (?, ?, ?, ?, 'transaction', ?)", snapshots);
        jdbc.execute("ANALYZE transactions");
        jdbc.execute("ANALYZE account_balance_history");
    }

    private UUID newCategory(String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }
}
