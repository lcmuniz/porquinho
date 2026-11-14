package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Goal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 8.2: contributions to the same goal are serialized by the row lock (findActiveByIdAndUserIdForUpdate), so
 * none is lost and the month's goal_allocations row is created only once.
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
}
