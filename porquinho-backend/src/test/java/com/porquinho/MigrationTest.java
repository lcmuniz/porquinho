package com.porquinho;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify Flyway migrations execute successfully.
 * Runs against PostgreSQL (Testcontainers), the same engine as production.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateUsersTableSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_name = 'users' AND table_schema = 'public'",
            Integer.class
        );
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void usersTableShouldHaveCorrectColumns() {
        String[] expectedColumns = {
            "id", "email", "auth_provider", "google_id",
            "created_at", "updated_at", "deleted_at", "deleted_by"
        };

        for (String column : expectedColumns) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'users' AND column_name = ?",
                Integer.class,
                column
            );
            assertThat(count)
                .withFailMessage("Column '%s' should exist in users table", column)
                .isEqualTo(1);
        }
    }

    @Test
    void usersTableShouldHaveUniqueEmailIndex() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_email'",
            Integer.class
        );
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void usersTableShouldHaveGoogleIdIndex() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_google_id'",
            Integer.class
        );
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void v9ShouldCreateSubscriptionsTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_name = 'subscriptions' AND table_schema = 'public'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v9SubscriptionsShouldHaveUniqueConstraintOnUserId() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'subscriptions' " +
            "AND constraint_name = 'subscriptions_user_id_unique' AND constraint_type = 'UNIQUE'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v11ShouldAddTrialReminderColumnsToSubscriptions() {
        for (String column : new String[] {"trial_reminder_7d_sent_at", "trial_reminder_1d_sent_at"}) {
            String type = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_name = 'subscriptions' AND column_name = ?",
                String.class,
                column
            );
            assertThat(type)
                .withFailMessage("Column '%s' should be TIMESTAMPTZ in subscriptions, got %s", column, type)
                .isEqualTo("timestamp with time zone");
        }
    }

    @Test
    void v13ShouldCreateBillingTransactionsTable() {
        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("id", "uuid");
        expected.put("subscription_id", "uuid");
        expected.put("user_id", "uuid");
        expected.put("amount", "numeric");
        expected.put("currency", "character varying");
        expected.put("status", "character varying");
        expected.put("payment_method", "character varying");
        expected.put("stripe_transaction_id", "character varying");
        expected.put("paid_at", "timestamp with time zone");
        expected.put("created_at", "timestamp with time zone");

        expected.forEach((column, type) -> {
            String actual = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_name = 'billing_transactions' AND column_name = ?",
                String.class,
                column
            );
            assertThat(actual)
                .withFailMessage("Column '%s' should be %s in billing_transactions, got %s", column, type, actual)
                .isEqualTo(type);
        });

        Integer precision = jdbcTemplate.queryForObject(
            "SELECT numeric_precision FROM information_schema.columns " +
            "WHERE table_name = 'billing_transactions' AND column_name = 'amount'", Integer.class);
        Integer scale = jdbcTemplate.queryForObject(
            "SELECT numeric_scale FROM information_schema.columns " +
            "WHERE table_name = 'billing_transactions' AND column_name = 'amount'", Integer.class);
        assertThat(precision).isEqualTo(15);
        assertThat(scale).isEqualTo(2);
    }

    @Test
    void v13BillingTransactionsShouldHaveUniqueStripeIdAndIndexedForeignKeys() {
        Integer unique = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'billing_transactions' " +
            "AND constraint_name = 'billing_transactions_stripe_transaction_id_unique' AND constraint_type = 'UNIQUE'",
            Integer.class
        );
        assertThat(unique).isEqualTo(1);

        Integer foreignKeys = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'billing_transactions' AND constraint_type = 'FOREIGN KEY'",
            Integer.class
        );
        assertThat(foreignKeys).isEqualTo(2);

        for (String index : new String[] {"idx_billing_transactions_subscription_id", "idx_billing_transactions_user_id"}) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'billing_transactions' AND indexname = ?",
                Integer.class, index);
            assertThat(count).withFailMessage("Index '%s' should exist", index).isEqualTo(1);
        }
    }
}
