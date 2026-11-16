package com.porquinho.repository;

import com.porquinho.entity.BudgetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repository for BudgetAllocation (Story 6.3). Every query and write is scoped by user_id. */
@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, UUID> {

    /** The user's allocations of the month ({@code monthYear} is day 1). */
    List<BudgetAllocation> findByUserIdAndMonthYear(UUID userId, LocalDate monthYear);

    /** The user's allocations of the category up to {@code monthYear} (day 1), oldest first (Story 6.8). */
    List<BudgetAllocation> findByUserIdAndCategoryIdAndMonthYearLessThanEqualOrderByMonthYear(
        UUID userId, UUID categoryId, LocalDate monthYear);

    /** The user's allocations of the category from {@code monthYear} (day 1) on (retro Epic 6 A4). */
    List<BudgetAllocation> findByUserIdAndCategoryIdAndMonthYearGreaterThanEqual(
        UUID userId, UUID categoryId, LocalDate monthYear);

    /**
     * Deletes the user's allocations of the month for {@code categoryIds} (the active children: rows of a category
     * removed since are kept as history). A bulk statement run before the inserts of a save, so the
     * UNIQUE (user_id, category_id, month_year) never sees the old and the new row at once (Hibernate flushes inserts
     * before deletes).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM BudgetAllocation b WHERE b.userId = :userId AND b.monthYear = :monthYear "
        + "AND b.categoryId IN :categoryIds")
    int deleteByUserIdAndMonthYearAndCategoryIdIn(@Param("userId") UUID userId, @Param("monthYear") LocalDate monthYear,
                                                  @Param("categoryIds") Collection<UUID> categoryIds);

    /**
     * Users with an allocation above zero in the month ({@code monthYear} is day 1) and the budget alert e-mail on
     * (pre-Epic 10). Soft-deleted users left out; access and pending purge are checked per user.
     */
    @Query(value = "SELECT DISTINCT b.user_id FROM budget_allocations b JOIN users u ON u.id = b.user_id "
        + "WHERE b.month_year = :monthYear AND b.allocated_amount > 0 "
        + "AND u.budget_alert_email_enabled AND u.deleted_at IS NULL", nativeQuery = true)
    List<UUID> findBudgetAlertCandidates(@Param("monthYear") LocalDate monthYear);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT b FROM BudgetAllocation b WHERE b.userId = :userId ORDER BY b.createdAt ASC, b.id ASC")
    List<BudgetAllocation> findByUserIdOrdered(@Param("userId") UUID userId);

    /** Hard-delete the user's rows (LGPD account purge). Must run BEFORE deleting the categories and the user row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM budget_allocations WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
