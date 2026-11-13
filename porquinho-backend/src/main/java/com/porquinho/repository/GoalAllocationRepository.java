package com.porquinho.repository;

import com.porquinho.entity.GoalAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for GoalAllocation (Story 8.2). Every query and write is scoped by user_id. */
@Repository
public interface GoalAllocationRepository extends JpaRepository<GoalAllocation, UUID> {

    /** The goal's row of the month ({@code monthYear} is day 1). */
    Optional<GoalAllocation> findByUserIdAndGoalIdAndMonthYear(UUID userId, UUID goalId, LocalDate monthYear);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT a FROM GoalAllocation a WHERE a.userId = :userId ORDER BY a.createdAt ASC, a.id ASC")
    List<GoalAllocation> findByUserIdOrdered(@Param("userId") UUID userId);

    /** Hard-delete the user's rows (LGPD account purge). Must run BEFORE deleting the goals and the user row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM goal_allocations WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
