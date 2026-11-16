package com.porquinho;

import com.porquinho.entity.AccountDeletion;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pre-Epic 10: V45 starts the 30 days of everyone already without access at the deploy (access_ended_at = now),
 * leaves accounts with access alone, and hashes the e-mail of deletions still pending. Own Testcontainers database
 * migrated to V44, seeded, then V45 applied.
 */
class V45RetentionMigrationTest {

    @Test
    void backfillCountsFromTheDeployOnlyForAccountsWithoutAccess() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            // TC_DAEMON: DriverManagerDataSource closes each connection; keep the container alive between them
            "jdbc:tc:postgresql:16-alpine:///v45_retention_test?TC_DAEMON=true");
        dataSource.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("44").load().migrate();

        UUID oldTrial = user(jdbc, "trial-velho@example.com");
        UUID runningTrial = user(jdbc, "trial@example.com");
        UUID suspended = user(jdbc, "suspensa@example.com");
        UUID cancelledPaid = user(jdbc, "cancelada@example.com");
        UUID cancelledOver = user(jdbc, "cancelada-fim@example.com");
        UUID monthly = user(jdbc, "mensal@example.com");
        UUID deleting = user(jdbc, " Ana@Example.com ");
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, trial_end_date) "
            + "VALUES (?, 'TRIAL', 'ACTIVE', now() - interval '90 days')", oldTrial);
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, trial_end_date) "
            + "VALUES (?, 'TRIAL', 'ACTIVE', now() + interval '5 days')", runningTrial);
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, next_billing_date) "
            + "VALUES (?, 'MONTHLY', 'SUSPENDED', now() - interval '60 days')", suspended);
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, next_billing_date) "
            + "VALUES (?, 'MONTHLY', 'CANCELLED', now() + interval '10 days')", cancelledPaid);
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, next_billing_date) "
            + "VALUES (?, 'MONTHLY', 'CANCELLED', now() - interval '40 days')", cancelledOver);
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, next_billing_date) "
            + "VALUES (?, 'MONTHLY', 'ACTIVE', now() - interval '40 days')", monthly);
        jdbc.update("INSERT INTO account_deletions (user_id, scheduled_purge_at) VALUES (?, now() + interval '20 days')",
            deleting);

        Flyway.configure().dataSource(dataSource).load().migrate();

        String hasEnd = "SELECT access_ended_at > now() - interval '1 minute' FROM subscriptions WHERE user_id = ?";
        String noEnd = "SELECT access_ended_at IS NULL FROM subscriptions WHERE user_id = ?";
        assertThat(jdbc.queryForObject(hasEnd, Boolean.class, oldTrial)).isTrue();
        assertThat(jdbc.queryForObject(hasEnd, Boolean.class, suspended)).isTrue();
        assertThat(jdbc.queryForObject(hasEnd, Boolean.class, cancelledOver)).isTrue();
        assertThat(jdbc.queryForObject(noEnd, Boolean.class, runningTrial)).isTrue();
        assertThat(jdbc.queryForObject(noEnd, Boolean.class, cancelledPaid)).isTrue();
        assertThat(jdbc.queryForObject(noEnd, Boolean.class, monthly)).isTrue();

        assertThat(jdbc.queryForObject("SELECT email_hash FROM account_deletions WHERE user_id = ?", String.class,
            deleting)).isEqualTo(AccountDeletion.hashEmail("ana@example.com"));
        assertThat(jdbc.queryForObject("SELECT reason FROM account_deletions WHERE user_id = ?", String.class,
            deleting)).isEqualTo("USER_REQUEST");
    }

    private static UUID user(JdbcTemplate jdbc, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, email);
        return id;
    }
}
