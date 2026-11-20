package com.porquinho.repository;

import com.porquinho.entity.AiClassificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
     * prediction, with the category kept only then; saving again recomputes both fields and moves reviewed_at.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE ai_classification_history SET "
        + "was_corrected = (predicted_category_id IS DISTINCT FROM CAST(:categoryId AS UUID)), "
        + "corrected_category_id = CASE WHEN predicted_category_id IS DISTINCT FROM CAST(:categoryId AS UUID) "
        + "THEN CAST(:categoryId AS UUID) END, reviewed_at = now() "
        + "WHERE user_id = :userId AND transaction_id IN (:ids)", nativeQuery = true)
    int markUserChoice(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids,
                       @Param("categoryId") UUID categoryId);

    /**
     * The import {@code importId} was completed: its rows get the completion as review date, also the ones the user
     * corrected during the review (decision of 2026-11-20: the panel counts an import only once it is completed).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE ai_classification_history h SET reviewed_at = now() FROM transactions t "
        + "WHERE t.id = h.transaction_id AND h.user_id = :userId AND t.import_id = :importId "
        + "AND t.review_status <> 'PENDING'", nativeQuery = true)
    int markReviewed(@Param("userId") UUID userId, @Param("importId") UUID importId);

    /**
     * Hard-delete the user's rows (LGPD account purge, Story 5.8). Must run BEFORE deleting the transactions, the
     * categories and the user row (FKs to all of them).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM ai_classification_history WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);

    // Story 10.3: aggregates of the admin panel over every user. A row counts once its transaction left the review
    // (not PENDING), its import was completed (decision of 2026-11-20: nothing of an import in review counts) and it
    // was not deleted; was_corrected is an AI error. The bank is
    // the one of the import, a null bank_name is UNKNOWN. No index for these: they scan, fine at the current volume.
    // Retro do Epic 10, A3: the period (and the month) is the review date, so a correction made the day after the
    // import enters the 24 h window of the 10.5 alert; the panel uses the same date, so the alert link matches it.

    String COUNTED = "FROM ai_classification_history h JOIN transactions t ON t.id = h.transaction_id "
        + "LEFT JOIN transaction_imports i ON i.id = t.import_id "
        + "WHERE t.deleted_at IS NULL AND t.review_status <> 'PENDING' "
        + "AND (t.import_id IS NULL OR i.status = 'COMPLETED') "
        + "AND h.reviewed_at >= :from AND h.reviewed_at < :to ";

    interface BankCounts {
        String getBank();
        long getClassifications();
        long getCorrections();
    }

    @Query(value = "SELECT COALESCE(i.bank_name, 'UNKNOWN') AS bank, count(*) AS classifications, "
        + "count(*) FILTER (WHERE h.was_corrected) AS corrections " + COUNTED
        + "AND (CAST(:bank AS varchar) IS NULL OR COALESCE(i.bank_name, 'UNKNOWN') = CAST(:bank AS varchar)) "
        + "GROUP BY 1", nativeQuery = true)
    List<BankCounts> countByBank(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                 @Param("bank") String bank);

    interface MonthCounts extends BankCounts {
        /** YYYY-MM in America/Sao_Paulo. */
        String getMonth();
    }

    @Query(value = "SELECT COALESCE(i.bank_name, 'UNKNOWN') AS bank, "
        + "to_char(h.reviewed_at AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') AS month, count(*) AS classifications, "
        + "count(*) FILTER (WHERE h.was_corrected) AS corrections " + COUNTED
        + "GROUP BY 1, 2", nativeQuery = true)
    List<MonthCounts> countByBankAndMonth(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    interface KindCounts {
        /** PIX, INSTALLMENT, DEBIT or CREDIT, in this precedence. */
        String getType();
        /** 0-49, 50-79 or 80-100. */
        String getBand();
        long getClassifications();
        long getCorrections();
    }

    @Query(value = "SELECT CASE WHEN t.is_pix THEN 'PIX' WHEN t.is_installment THEN 'INSTALLMENT' "
        + "ELSE t.transaction_type END AS type, "
        + "CASE WHEN h.confidence < 50 THEN '0-49' WHEN h.confidence < 80 THEN '50-79' ELSE '80-100' END AS band, "
        + "count(*) AS classifications, count(*) FILTER (WHERE h.was_corrected) AS corrections " + COUNTED
        + "AND COALESCE(i.bank_name, 'UNKNOWN') = :bank GROUP BY 1, 2", nativeQuery = true)
    List<KindCounts> countByTypeAndBand(@Param("bank") String bank, @Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to);
}
