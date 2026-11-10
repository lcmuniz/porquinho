package com.porquinho.repository;

import com.porquinho.entity.BudgetReallocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository for BudgetReallocation (Story 6.5). Every query and write is scoped by user_id. */
@Repository
public interface BudgetReallocationRepository extends JpaRepository<BudgetReallocation, UUID> {

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT r FROM BudgetReallocation r WHERE r.userId = :userId ORDER BY r.createdAt ASC, r.id ASC")
    List<BudgetReallocation> findByUserIdOrdered(@Param("userId") UUID userId);

    /** Hard-delete the user's rows (LGPD account purge). Must run BEFORE deleting the categories and the user row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM budget_reallocations WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
