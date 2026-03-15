package com.porquinho;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify Flyway migrations execute successfully.
 * Tests V2__create_users_table.sql migration.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateUsersTableSuccessfully() {
        // Verify users table exists (H2 uses uppercase table names)
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE UPPER(table_name) = 'USERS' AND table_schema = 'PUBLIC'",
            Integer.class
        );
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void usersTableShouldHaveCorrectColumns() {
        // Verify required columns exist (H2 uses uppercase column names)
        String[] expectedColumns = {
            "ID", "EMAIL", "AUTH_PROVIDER", "GOOGLE_ID",
            "CREATED_AT", "UPDATED_AT", "DELETED_AT", "DELETED_BY"
        };

        for (String column : expectedColumns) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE UPPER(table_name) = 'USERS' AND UPPER(column_name) = ?",
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
        // Verify idx_users_email index exists (H2 uses information_schema.indexes)
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.indexes " +
            "WHERE UPPER(table_name) = 'USERS' AND UPPER(index_name) = 'IDX_USERS_EMAIL'",
            Integer.class
        );
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void usersTableShouldHaveGoogleIdIndex() {
        // Verify idx_users_google_id index exists (H2 uses information_schema.indexes)
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.indexes " +
            "WHERE UPPER(table_name) = 'USERS' AND UPPER(index_name) = 'IDX_USERS_GOOGLE_ID'",
            Integer.class
        );
        assertThat(count).isGreaterThan(0);
    }
}
