package com.porquinho.repository;

import com.porquinho.entity.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Category (Stories 3.4 and 3.5). Every lookup is scoped by user_id; "active" means deleted_at IS NULL.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * The user's active categories, by name then id (Story 3.5: deterministic order). The frontend re-sorts with
     * the pt-BR collation; this keeps the API stable regardless of the database collation.
     */
    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.categoryName ASC, c.id ASC")
    List<Category> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * The user's active categories, locked (SELECT ... FOR UPDATE) until the end of the transaction (Story 3.5).
     * Called first by every category mutation, so concurrent mutations of the same user are serialized.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.id ASC")
    List<Category> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    /** Every row of the user, removed ones included (LGPD export). */
    List<Category> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /** The user's categories among {@code ids}, removed ones included: the review shows the name saved (Story 5.9). */
    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND c.id IN :ids")
    List<Category> findByUserIdAndIdIn(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    /**
     * Hard-delete the user's categories (LGPD account purge, Story 3.4). A single statement, so the
     * self-referencing FK (parent_category_id) does not block it: every row of the user goes at once.
     * Must run BEFORE deleting the user row (FK categories.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM categories WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
