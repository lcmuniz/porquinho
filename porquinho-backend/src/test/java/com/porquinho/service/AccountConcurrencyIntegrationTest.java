package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 4.4: removals of the same user's accounts are serialized by row locks (onboarding progress, then the active
 * accounts), so two concurrent removals cannot leave a user who finished the onboarding without an active account.
 * Not @Transactional: each service call commits on its own thread, so requests really race. Cleans up after.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountConcurrencyIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        jdbc.update("INSERT INTO onboarding_progress (user_id, current_step, is_completed) VALUES (?, 8, true)", userId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM onboarding_progress WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void twoConcurrentRemovalsOfTheLastTwoAccountsLeaveOneActive() throws Exception {
        List<UUID> accounts = List.of("Nubank", "BB").stream()
            .map(name -> accountService.create(userId,
                new CreateAccountRequest(name, null, "CHECKING", BigDecimal.TEN)).getId())
            .toList();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = accounts.stream().map(id -> pool.submit(() -> {
                start.await();
                try {
                    accountService.delete(userId, id, "127.0.0.1");
                    return "204";
                } catch (ConflictException e) {
                    return e.getMessage();
                }
            })).toList();
            start.countDown();

            List<String> outcomes = results.stream().map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            assertThat(outcomes).containsExactlyInAnyOrder("204", AccountService.LAST_ACTIVE_MESSAGE);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE user_id = ? AND deleted_at IS NULL", Long.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'account_deleted'", Long.class, userId))
            .isEqualTo(1);
    }
}
