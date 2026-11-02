package com.porquinho.repository;

import com.porquinho.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Goal (Story 3.8). Every lookup is scoped by user_id; "active" means deleted_at IS NULL.
 */
@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /** The user's active goals, oldest first (id breaks ties for rows created in the same instant). */
    @Query("SELECT g FROM Goal g WHERE g.userId = :userId AND g.deletedAt IS NULL ORDER BY g.createdAt ASC, g.id ASC")
    List<Goal> findActiveByUserId(@Param("userId") UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    /** Every row of the user, removed ones included (LGPD export). */
    List<Goal> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Hard-delete the user's goals (LGPD account purge, Story 3.8).
     * Must run BEFORE deleting the user row (FK goals.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM goals WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
