package com.porquinho.repository;

import com.porquinho.entity.UserClassificationPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repository for UserClassificationPattern (Story 5.11). Every query and write is scoped by user_id. */
@Repository
public interface UserClassificationPatternRepository extends JpaRepository<UserClassificationPattern, UUID> {

    /**
     * Records that the user chose {@code categoryId} for {@code pattern} (decision Q2): the same category adds one to
     * {@code usage_count}, a new pattern or another category starts it again at 1.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO user_classification_patterns "
        + "(user_id, description_pattern, preferred_category_id, usage_count, last_used_at, created_at) "
        + "VALUES (:userId, :pattern, :categoryId, 1, :now, :now) "
        + "ON CONFLICT (user_id, description_pattern) DO UPDATE SET "
        + "usage_count = CASE WHEN user_classification_patterns.preferred_category_id = EXCLUDED.preferred_category_id "
        + "THEN user_classification_patterns.usage_count + 1 ELSE 1 END, "
        + "preferred_category_id = EXCLUDED.preferred_category_id, last_used_at = EXCLUDED.last_used_at",
        nativeQuery = true)
    int upsert(@Param("userId") UUID userId, @Param("pattern") String pattern, @Param("categoryId") UUID categoryId,
               @Param("now") OffsetDateTime now);

    /**
     * The user's patterns whose category is still an active child category of the user: one removed or turned into a
     * group is left out, so its rows go to the AI.
     */
    @Query("SELECT p FROM UserClassificationPattern p, Category c WHERE p.userId = :userId "
        + "AND c.id = p.preferredCategoryId AND c.userId = :userId AND c.deletedAt IS NULL "
        + "AND c.parentCategoryId IS NOT NULL")
    List<UserClassificationPattern> findUsableByUserId(@Param("userId") UUID userId);

    /** Marks the user's {@code patterns} as applied by an import. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserClassificationPattern p SET p.lastUsedAt = :now "
        + "WHERE p.userId = :userId AND p.descriptionPattern IN :patterns")
    int touch(@Param("userId") UUID userId, @Param("patterns") Collection<String> patterns,
              @Param("now") OffsetDateTime now);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT p FROM UserClassificationPattern p WHERE p.userId = :userId ORDER BY p.createdAt ASC, p.id ASC")
    List<UserClassificationPattern> findByUserIdOrdered(@Param("userId") UUID userId);

    /** Hard-delete the user's rows (LGPD account purge). Must run BEFORE deleting the categories and the user row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_classification_patterns WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
