package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Goal;
import com.porquinho.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 8.2: contributions to the same goal are serialized by the row lock (findActiveByIdAndUserIdForUpdate), so
 * none is lost and the month's goal_allocations row is created only once.
 * Retro Epic 8 A2: the same lock makes the completion (Story 8.7) happen once when two contributions cross the target.
 * Not @Transactional: each service call commits on its own thread, so the requests really race. Cleans up after.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoalConcurrencyIntegrationTest {

    @Autowired
    private GoalService goalService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM goal_progress_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM goal_allocations WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM goals WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void twoConcurrentContributionsInAMonthWithoutRowAreBothKeptInOneRow() throws Exception {
        Goal goal = goalService.create(userId,
            new CreateGoalRequest("Viagem", new BigDecimal("1000"), null, new BigDecimal("300"), null));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Goal>> results = List.of("50.00", "25.50").stream().map(amount -> pool.submit(() -> {
                start.await();
                return goalService.contribute(userId, goal.getId(), new BigDecimal(amount), "127.0.0.1");
            })).toList();
            start.countDown();
            for (Future<Goal> result : results) result.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT current_amount FROM goals WHERE id = ?", BigDecimal.class, goal.getId()))
            .isEqualByComparingTo("75.50");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT allocated_amount, contributed_amount FROM goal_allocations WHERE goal_id = ?", goal.getId());
        assertThat(rows).hasSize(1);
        assertThat((BigDecimal) rows.get(0).get("contributed_amount")).isEqualByComparingTo("75.50");
        assertThat((BigDecimal) rows.get(0).get("allocated_amount")).isEqualByComparingTo("300");
    }

    @Test
    void twoConcurrentContributionsCrossingTheTargetCompleteTheGoalOnce() throws Exception {
        Goal goal = goalService.create(userId,
            new CreateGoalRequest("Viagem", new BigDecimal("1000"), null, new BigDecimal("300"), null));
        goalService.contribute(userId, goal.getId(), new BigDecimal("900"), "127.0.0.1");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<BigDecimal> kept = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        try {
            List<String> amounts = List.of("200.00", "300.00");
            List<Future<Goal>> results = amounts.stream().map(amount -> pool.submit(() -> {
                start.await();
                return goalService.contribute(userId, goal.getId(), new BigDecimal(amount), "127.0.0.1");
            })).toList();
            start.countDown();
            for (int i = 0; i < results.size(); i++) {
                try {
                    results.get(i).get(30, TimeUnit.SECONDS);
                    kept.add(new BigDecimal(amounts.get(i)));
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // One contribution wins and completes the goal; the other sees it completed and gets the 409
        assertThat(kept).hasSize(1);
        assertThat(failures).singleElement().isInstanceOf(ConflictException.class);
        BigDecimal winner = kept.get(0);
        Map<String, Object> row = jdbc.queryForMap("SELECT current_amount, is_completed, completed_at FROM goals WHERE id = ?", goal.getId());
        assertThat((BigDecimal) row.get("current_amount")).isEqualByComparingTo(new BigDecimal("900").add(winner));
        assertThat(row.get("is_completed")).isEqualTo(true);
        assertThat(row.get("completed_at")).isNotNull();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = ?", Integer.class,
            userId, GoalService.AUDIT_COMPLETED)).isEqualTo(1);
        // Creation (0, no contribution), the 900 and only the winner: the loser rolled back its history line.
        // Ordered by amount_snapshot (it only grows here): created_at could tie
        List<Map<String, Object>> history = jdbc.queryForList(
            "SELECT amount_snapshot, contribution_amount FROM goal_progress_history WHERE goal_id = ? ORDER BY amount_snapshot",
            goal.getId());
        assertThat(history).hasSize(3);
        assertThat(history.get(0).get("contribution_amount")).isNull();
        assertThat((BigDecimal) history.get(1).get("contribution_amount")).isEqualByComparingTo("900");
        assertThat((BigDecimal) history.get(2).get("contribution_amount")).isEqualByComparingTo(winner);
        assertThat((BigDecimal) history.get(2).get("amount_snapshot")).isEqualByComparingTo(new BigDecimal("900").add(winner));
    }
}
