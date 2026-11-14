package com.porquinho.repository;

import com.porquinho.entity.GoalProgressHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Repository for GoalProgressHistory (Story 8.8). Every query and write is scoped by user_id. */
@Repository
public interface GoalProgressHistoryRepository extends JpaRepository<GoalProgressHistory, UUID> {

    /** The goal's snapshots, oldest first. */
    @Query("SELECT h FROM GoalProgressHistory h WHERE h.userId = :userId AND h.goalId = :goalId "
        + "ORDER BY h.snapshotDate ASC, h.createdAt ASC, h.id ASC")
    List<GoalProgressHistory> findByUserIdAndGoalIdOrdered(@Param("userId") UUID userId, @Param("goalId") UUID goalId);

    /** Monthly job idempotency: the goal already has a snapshot without contribution on that date. */
    boolean existsByUserIdAndGoalIdAndSnapshotDateAndContributionAmountIsNull(UUID userId, UUID goalId, LocalDate snapshotDate);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT h FROM GoalProgressHistory h WHERE h.userId = :userId ORDER BY h.createdAt ASC, h.id ASC")
    List<GoalProgressHistory> findByUserIdOrdered(@Param("userId") UUID userId);

    /** Hard-delete the user's rows (LGPD account purge). Must run BEFORE deleting the goals and the user row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM goal_progress_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
