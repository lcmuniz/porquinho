package com.porquinho.repository;

import com.porquinho.entity.AiClassificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repository for AiClassificationHistory (Story 5.8). */
@Repository
public interface AiClassificationHistoryRepository extends JpaRepository<AiClassificationHistory, UUID> {

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT h FROM AiClassificationHistory h WHERE h.userId = :userId ORDER BY h.createdAt ASC, h.id ASC")
    List<AiClassificationHistory> findByUserIdOrdered(@Param("userId") UUID userId);

    /**
     * Story 5.11: the user saved {@code categoryId} on transactions {@code ids}. Corrected when it differs from the
     * prediction, with the category kept only then; saving again recomputes both fields.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE ai_classification_history SET "
        + "was_corrected = (predicted_category_id IS DISTINCT FROM CAST(:categoryId AS UUID)), "
        + "corrected_category_id = CASE WHEN predicted_category_id IS DISTINCT FROM CAST(:categoryId AS UUID) "
        + "THEN CAST(:categoryId AS UUID) END "
        + "WHERE user_id = :userId AND transaction_id IN (:ids)", nativeQuery = true)
    int markUserChoice(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids,
                       @Param("categoryId") UUID categoryId);

    /**
     * Hard-delete the user's rows (LGPD account purge, Story 5.8). Must run BEFORE deleting the transactions, the
     * categories and the user row (FKs to all of them).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM ai_classification_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
