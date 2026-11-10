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

    @Test
    void v14ShouldAddPaymentFailureColumnsToSubscriptions() {
        String attemptsType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'subscriptions' AND column_name = 'failed_payment_attempts'", String.class);
        assertThat(attemptsType).isEqualTo("integer");
        String attemptsNullable = jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_name = 'subscriptions' AND column_name = 'failed_payment_attempts'", String.class);
        assertThat(attemptsNullable).isEqualTo("NO");
        String attemptsDefault = jdbcTemplate.queryForObject(
            "SELECT column_default FROM information_schema.columns " +
            "WHERE table_name = 'subscriptions' AND column_name = 'failed_payment_attempts'", String.class);
        assertThat(attemptsDefault).isEqualTo("0");

        Integer urlLength = jdbcTemplate.queryForObject(
            "SELECT character_maximum_length FROM information_schema.columns " +
            "WHERE table_name = 'subscriptions' AND column_name = 'payment_update_url'", Integer.class);
        assertThat(urlLength).isEqualTo(1024);
    }

    @Test
    void v14StatusCheckShouldAcceptPaymentFailedAndSuspendedOnly() {
        String definition = jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
            "WHERE conrelid = 'subscriptions'::regclass AND conname = 'subscriptions_status_check'", String.class);
        assertThat(definition).contains("ACTIVE", "CANCELLED", "EXPIRED", "PAYMENT_FAILED", "SUSPENDED");

        Integer statusChecks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'subscriptions'::regclass " +
            "AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%status%'", Integer.class);
        assertThat(statusChecks).isEqualTo(1);
    }

    @Test
    void v25ShouldCreateTransactionImportsTable() {
        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("id", "uuid");
        expected.put("user_id", "uuid");
        expected.put("account_id", "uuid");
        expected.put("credit_card_id", "uuid");
        expected.put("file_name", "character varying");
        expected.put("file_type", "character varying");
        expected.put("bank_name", "character varying");
        expected.put("file_size_kb", "integer");
        expected.put("status", "character varying");
        expected.put("total_transactions", "integer");
        expected.put("imported_transactions", "integer");
        expected.put("duplicates_found", "integer");
        expected.put("transfers_found", "integer");  // V27 (Story 5.5)
        expected.put("skipped_rows", "integer");
        expected.put("error_message", "character varying");
        expected.put("created_at", "timestamp with time zone");
        expected.put("updated_at", "timestamp with time zone");

        expected.forEach((column, type) -> {
            String actual = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_name = 'transaction_imports' AND column_name = ?",
                String.class,
                column
            );
            assertThat(actual)
                .withFailMessage("Column '%s' should be %s in transaction_imports, got %s", column, type, actual)
                .isEqualTo(type);
        });

        Integer foreignKeys = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'transaction_imports' AND constraint_type = 'FOREIGN KEY'", Integer.class);
        assertThat(foreignKeys).isEqualTo(3);

        for (String check : new String[] {"transaction_imports_status_check", "transaction_imports_file_type_check",
            "transaction_imports_one_target_check", "transaction_imports_transfers_found_check"}) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'transaction_imports'::regclass AND conname = ?",
                Integer.class, check);
            assertThat(count).withFailMessage("Constraint '%s' should exist", check).isEqualTo(1);
        }

        Integer index = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'transaction_imports' " +
            "AND indexname = 'idx_transaction_imports_user_created'", Integer.class);
        assertThat(index).isEqualTo(1);
    }

    @Test
    void v29ShouldAcceptApprovedAndIndexTheConfirmingImport() {
        String check = jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'transactions_review_status_check'",
            String.class);
        assertThat(check).contains("PENDING").contains("APPROVED").contains("CONFIRMED");

        Integer index = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'transactions' " +
            "AND indexname = 'idx_transactions_confirmed_by_import_id'", Integer.class);
        assertThat(index).isEqualTo(1);
    }

    @Test
    void v26ShouldCreateTransactionsTable() {
        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("id", "uuid");
        expected.put("user_id", "uuid");
        expected.put("account_id", "uuid");
        expected.put("credit_card_id", "uuid");
        expected.put("import_id", "uuid");
        expected.put("external_id", "character varying");
        expected.put("transaction_date", "date");
        expected.put("description", "character varying");
        expected.put("original_description", "character varying");
        expected.put("amount", "numeric");
        expected.put("transaction_type", "character varying");
        expected.put("kind", "character varying");
        expected.put("category_id", "uuid");
        expected.put("classification_source", "character varying");
        expected.put("classification_confidence", "integer");
        expected.put("review_status", "character varying");
        expected.put("affects_balance", "boolean");
        expected.put("is_pix", "boolean");
        expected.put("pix_details", "jsonb");
        expected.put("is_installment", "boolean");
        expected.put("installment_number", "integer");
        expected.put("installment_total", "integer");
        expected.put("installment_group_id", "uuid");
        expected.put("is_future_installment", "boolean");
        expected.put("confirmed_by_import_id", "uuid"); // V29
        expected.put("created_at", "timestamp with time zone");
        expected.put("updated_at", "timestamp with time zone");
        expected.put("deleted_at", "timestamp with time zone");

        expected.forEach((column, type) -> {
            String actual = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_name = 'transactions' AND column_name = ?",
                String.class,
                column
            );
            assertThat(actual)
                .withFailMessage("Column '%s' should be %s in transactions, got %s", column, type, actual)
                .isEqualTo(type);
        });

        Integer amountScale = jdbcTemplate.queryForObject(
            "SELECT numeric_scale FROM information_schema.columns WHERE table_name = 'transactions' " +
            "AND column_name = 'amount'", Integer.class);
        assertThat(amountScale).isEqualTo(2);

        Integer foreignKeys = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'transactions' AND constraint_type = 'FOREIGN KEY'", Integer.class);
        assertThat(foreignKeys).isEqualTo(6);

        Integer cascades = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'transactions'::regclass AND contype = 'f' " +
            "AND confdeltype <> 'a'", Integer.class);
        assertThat(cascades).withFailMessage("FKs of transactions must have no ON DELETE action").isZero();

        for (String check : new String[] {"transactions_one_target_check", "transactions_amount_check",
            "transactions_type_check", "transactions_kind_check", "transactions_classification_source_check",
            "transactions_classification_confidence_check", "transactions_review_status_check"}) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'transactions'::regclass AND conname = ?",
                Integer.class, check);
            assertThat(count).withFailMessage("Constraint '%s' should exist", check).isEqualTo(1);
        }

        for (String index : new String[] {"idx_transactions_user_id", "idx_transactions_import_id",
            "idx_transactions_account_date", "idx_transactions_credit_card_date"}) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'transactions' AND indexname = ?",
                Integer.class, index);
            assertThat(count).withFailMessage("Index '%s' should exist", index).isEqualTo(1);
        }
    }

    @Test
    void everyPublicTableShouldHaveRowLevelSecurityEnabled() {
        // afterMigrate.sql: sem RLS, o PostgREST do Supabase expõe a tabela para a anon key
        var withoutRls = jdbcTemplate.queryForList(
            "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
            "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity", String.class);
        assertThat(withoutRls).isEmpty();
    }
}
