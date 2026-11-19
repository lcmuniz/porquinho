package com.porquinho.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retro Epic 7 A2: the plans of the dashboard reads use the V38 indexes and the LATERAL with LIMIT 1. Tables are small
 * in the tests, so {@code SET LOCAL enable_seqscan = off} (this test's transaction only) takes the seq scan out of the
 * planner's choices; what is asserted is which index it picks, not the cost. The LATERAL query is read from the
 * repository's own @Query, so going back to DISTINCT ON fails here.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardQueryPlanIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        for (int a = 0; a < 3; a++) {
            UUID account = UUID.randomUUID();
            jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
                + "VALUES (?, ?, 'Conta', 'CHECKING', 0, 0)", account, userId);
            List<Object[]> transactions = new ArrayList<>();
            List<Object[]> snapshots = new ArrayList<>();
            for (int d = 0; d < 200; d++) {
                transactions.add(new Object[] {userId, account, d});
                snapshots.add(new Object[] {account, userId, d});
            }
            jdbc.batchUpdate("INSERT INTO transactions (user_id, account_id, transaction_date, description, "
                + "original_description, amount, transaction_type, review_status) VALUES (?, ?, DATE '2026-01-01' + ?, "
                + "'X', 'X', 1, 'DEBIT', 'CONFIRMED')", transactions);
            jdbc.batchUpdate("INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, "
                + "created_by_event) VALUES (?, ?, 0, TIMESTAMPTZ '2026-01-01 12:00:00+00' + make_interval(days => ?), "
                + "'transaction')", snapshots);
        }
        jdbc.execute("ANALYZE transactions");
        jdbc.execute("ANALYZE account_balance_history");
        jdbc.execute("ANALYZE accounts");
        jdbc.execute("SET LOCAL enable_seqscan = off");
    }

    private String plan(String sql) {
        return String.join("\n", jdbc.queryForList("EXPLAIN " + sql, String.class));
    }

    @Test
    void sumsByDateUseTheUserDateIndexOfTransactions() {
        String plan = plan("SELECT category_id, SUM(amount) FROM transactions WHERE user_id = '" + userId + "' "
            + "AND transaction_date BETWEEN DATE '2026-03-01' AND DATE '2026-03-31' AND deleted_at IS NULL "
            + "GROUP BY category_id");

        assertThat(plan).contains("idx_transactions_user_date").containsPattern("Index Cond: .*transaction_date >=");
    }

    @Test
    void wealthCurveUsesTheUserDateIndexOfTheHistory() {
        String plan = plan("SELECT * FROM account_balance_history WHERE user_id = '" + userId + "' "
            + "AND snapshot_date >= TIMESTAMPTZ '2026-06-01 00:00:00+00' ORDER BY snapshot_date, created_at, id");

        assertThat(plan).contains("idx_account_balance_history_user_date").containsPattern("Index Cond: .*snapshot_date >=");
    }

    @Test
    void lastSnapshotPerAccountIsOneLimitedIndexLookupPerAccount() throws Exception {
        String sql = AccountBalanceHistoryRepository.class
            .getMethod("findLastPerAccountBefore", UUID.class, OffsetDateTime.class).getAnnotation(Query.class).value()
            .replace(":userId", "'" + userId + "'")
            .replace(":from", "TIMESTAMPTZ '2026-05-01 00:00:00+00'");

        String plan = plan(sql);

        // which of the two (account_id | user_id, snapshot_date) indexes the planner reads depends on the data spread;
        // what matters is one backward index read cut by LIMIT per account, not a sort of the whole history
        assertThat(plan).contains("Nested Loop", "Limit", "Index Scan Backward using idx_account_balance_history_")
            .doesNotContain("Unique");
    }
}
