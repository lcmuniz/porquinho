package com.porquinho;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 8.8: V37 backfill writes the initial snapshot (current amount minus the V34 contributions) and one entry per
 * month with contribution, with the running total; the creation month's entry is not dated before the creation.
 * Migrates a dedicated PostgreSQL (own Testcontainers database) to V36, seeds a goal, then applies V37.
 */
class V37BackfillMigrationTest {

    @Test
    void backfillWritesTheInitialSnapshotAndTheMonthlyContributions() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            // TC_DAEMON: DriverManagerDataSource closes each connection; keep the container alive between them
            "jdbc:tc:postgresql:16-alpine:///v37_backfill_test?TC_DAEMON=true");
        dataSource.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("36").load().migrate();

        UUID user = UUID.randomUUID();
        UUID goal = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, 'goal@example.com', 'EMAIL')", user);
        // Created 10/jan (São Paulo); 50 from before 8.2 + 100 (jan) + 200 (mar) = 350
        jdbc.update("INSERT INTO goals (id, user_id, goal_name, target_amount, current_amount, created_at) "
            + "VALUES (?, ?, 'Viagem', 1000, 350, TIMESTAMPTZ '2026-01-10 12:00:00+00')", goal, user);
        jdbc.update("INSERT INTO goal_allocations (user_id, goal_id, month_year, allocated_amount, contributed_amount) "
            + "VALUES (?, ?, DATE '2026-01-01', 100, 100), (?, ?, DATE '2026-02-01', 100, 0), "
            + "(?, ?, DATE '2026-03-01', 100, 200)", user, goal, user, goal, user, goal);

        Flyway.configure().dataSource(dataSource).load().migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, snapshot_date, contribution_amount, amount_snapshot FROM goal_progress_history "
                + "WHERE goal_id = ? ORDER BY snapshot_date, contribution_amount NULLS FIRST", goal);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("user_id")).isEqualTo(user));

        assertThat(rows.get(0).get("snapshot_date")).isEqualTo(Date.valueOf("2026-01-10"));
        assertThat(rows.get(0).get("contribution_amount")).isNull();
        assertThat((BigDecimal) rows.get(0).get("amount_snapshot")).isEqualByComparingTo("50");

        // Creation month: day 1 would come before the creation, so it takes the creation date
        assertThat(rows.get(1).get("snapshot_date")).isEqualTo(Date.valueOf("2026-01-10"));
        assertThat((BigDecimal) rows.get(1).get("contribution_amount")).isEqualByComparingTo("100");
        assertThat((BigDecimal) rows.get(1).get("amount_snapshot")).isEqualByComparingTo("150");

        // February (contributed 0) is skipped
        assertThat(rows.get(2).get("snapshot_date")).isEqualTo(Date.valueOf("2026-03-01"));
        assertThat((BigDecimal) rows.get(2).get("contribution_amount")).isEqualByComparingTo("200");
        assertThat((BigDecimal) rows.get(2).get("amount_snapshot")).isEqualByComparingTo("350");
    }
}
