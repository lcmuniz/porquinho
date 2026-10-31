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
}
