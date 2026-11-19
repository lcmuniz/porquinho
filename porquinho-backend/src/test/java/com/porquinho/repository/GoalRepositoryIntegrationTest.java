package com.porquinho.repository;

import com.porquinho.entity.GoalAllocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 8.3: the goal lookups (with and without row lock) and the progress series against the real PostgreSQL:
 * another user's goal and a removed goal are not found, and the months come oldest first whatever the insert order.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoalRepositoryIntegrationTest {

    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private GoalAllocationRepository goalAllocationRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID owner;
    private UUID stranger;

    @BeforeEach
    void setUp() {
        owner = newUser();
        stranger = newUser();
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        return id;
    }

    private UUID newGoal(UUID userId, boolean removed) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO goals (id, user_id, goal_name, target_amount, deleted_at) VALUES (?, ?, 'Viagem', 1000, "
            + (removed ? "now()" : "NULL") + ")", id, userId);
        return id;
    }

    private void allocation(UUID userId, UUID goalId, String month) {
        jdbc.update("INSERT INTO goal_allocations (user_id, goal_id, month_year, allocated_amount, contributed_amount) "
            + "VALUES (?, ?, ?::date, 100, 50)", userId, goalId, month);
    }

    @Test
    void activeGoalIsFoundOnlyByItsOwner() {
        UUID goal = newGoal(owner, false);

        assertThat(goalRepository.findActiveByIdAndUserId(goal, owner)).isPresent();
        assertThat(goalRepository.findActiveByIdAndUserIdForUpdate(goal, owner)).isPresent();
        assertThat(goalRepository.findActiveByIdAndUserId(goal, stranger)).isEmpty();
        assertThat(goalRepository.findActiveByIdAndUserIdForUpdate(goal, stranger)).isEmpty();
    }

    @Test
    void removedGoalIsNotFound() {
        UUID goal = newGoal(owner, true);

        assertThat(goalRepository.findActiveByIdAndUserId(goal, owner)).isEmpty();
        assertThat(goalRepository.findActiveByIdAndUserIdForUpdate(goal, owner)).isEmpty();
    }

    @Test
    void allocationsComeOldestMonthFirstAndOnlyTheOwnersOfThatGoal() {
        UUID goal = newGoal(owner, false);
        UUID otherGoal = newGoal(owner, false);
        allocation(owner, goal, "2026-03-01");
        allocation(owner, goal, "2025-12-01");
        allocation(owner, otherGoal, "2026-01-01");
        allocation(owner, goal, "2026-01-01");

        assertThat(goalAllocationRepository.findByUserIdAndGoalIdOrderByMonthYearAsc(owner, goal))
            .extracting(GoalAllocation::getMonthYear)
            .containsExactly(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));
        assertThat(goalAllocationRepository.findByUserIdAndGoalIdOrderByMonthYearAsc(stranger, goal)).isEmpty();
    }
}
