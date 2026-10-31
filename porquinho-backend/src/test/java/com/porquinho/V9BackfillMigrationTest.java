package com.porquinho;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 2.1: V9 backfill gives existing active users a 30-day trial and skips soft-deleted ones.
 * Migrates a dedicated PostgreSQL (own Testcontainers database) to V8, seeds users, then applies V9.
 */
class V9BackfillMigrationTest {

    @Test
    void backfillCreatesTrialOnlyForActiveUsers() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            // TC_DAEMON: DriverManagerDataSource closes each connection; keep the container alive between them
            "jdbc:tc:postgresql:16-alpine:///v9_backfill_test?TC_DAEMON=true");
        dataSource.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("8").load().migrate();

        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, 'active@example.com', 'EMAIL')", active);
        jdbc.update("INSERT INTO users (id, email, auth_provider, deleted_at) VALUES (?, 'gone@example.com', 'EMAIL', NOW())", deleted);

        Flyway.configure().dataSource(dataSource).load().migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, plan_type, status, trial_start_date, trial_end_date, "
                + "stripe_customer_id, stripe_subscription_id FROM subscriptions");
        assertThat(rows).hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(row.get("user_id")).isEqualTo(active);
        assertThat(row.get("plan_type")).isEqualTo("TRIAL");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        Instant start = ((Timestamp) row.get("trial_start_date")).toInstant();
        Instant end = ((Timestamp) row.get("trial_end_date")).toInstant();
        assertThat(Duration.between(start, end)).isEqualTo(Duration.ofDays(30));
        // Story 2.2: V10 adds the Stripe ids, empty during the trial
        assertThat(row.get("stripe_customer_id")).isNull();
        assertThat(row.get("stripe_subscription_id")).isNull();
    }
}
