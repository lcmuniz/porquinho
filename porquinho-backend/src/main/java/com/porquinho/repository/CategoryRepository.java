package com.porquinho.repository;

import com.porquinho.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Category (Story 3.4). Every lookup is scoped by user_id; "active" means deleted_at IS NULL.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** The user's active categories, oldest first (id breaks ties for rows created in the same instant). */
    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC, c.id ASC")
    List<Category> findActiveByUserId(@Param("userId") UUID userId);

    /** Every row of the user, removed ones included (LGPD export). */
    List<Category> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Hard-delete the user's categories (LGPD account purge, Story 3.4). A single statement, so the
     * self-referencing FK (parent_category_id) does not block it: every row of the user goes at once.
     * Must run BEFORE deleting the user row (FK categories.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM categories WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
