package com.porquinho.service;

import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Goal;
import com.porquinho.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Financial goals (Story 3.8: create and list; editing, contributions and the goals page come in Epic 8).
 * The user id always comes from the JWT; another user's goal is never returned.
 */
@Service
public class GoalService {

    private final GoalRepository goalRepository;

    public GoalService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    /**
     * Creates the goal with current_amount = 0. The controller already validated at most 2 decimals; HALF_UP
     * (instead of UNNECESSARY) keeps another caller from failing with an ArithmeticException.
     */
    @Transactional
    public Goal create(UUID userId, CreateGoalRequest request) {
        Goal goal = new Goal(userId, request.name(),
            request.targetAmount().setScale(2, RoundingMode.HALF_UP),
            request.targetDate(),
            request.monthlyAllocation() == null ? null
                : request.monthlyAllocation().setScale(2, RoundingMode.HALF_UP));
        goal.setDescription(request.description());
        return goalRepository.save(goal);
    }

    /** The user's active goals, oldest first. */
    @Transactional(readOnly = true)
    public List<Goal> listActive(UUID userId) {
        return goalRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countActive(UUID userId) {
        return goalRepository.countByUserIdAndDeletedAtIsNull(userId);
    }
}
