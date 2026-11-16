package com.porquinho.repository;

import com.porquinho.entity.BudgetAlertNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Dedup of the budget alert e-mails (pre-Epic 10). Every query and write is scoped by user_id. */
@Repository
public interface BudgetAlertNotificationRepository extends JpaRepository<BudgetAlertNotification, UUID> {

    /**
     * Records that the alert of (category, month, level) was sent; 1 when this call inserted the row, 0 when it already
     * existed. Atomic on the UNIQUE (user_id, category_id, month, level): two runs never both get 1.
     */
    @Modifying
    @Query(value = "INSERT INTO budget_alert_notifications (id, user_id, category_id, month, level, sent_at) "
        + "VALUES (gen_random_uuid(), :userId, :categoryId, :month, :level, :sentAt) ON CONFLICT DO NOTHING",
        nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId,
                       @Param("month") LocalDate month, @Param("level") short level,
                       @Param("sentAt") OffsetDateTime sentAt);

    /** Every row of the user, oldest first (LGPD export). */
    @Query("SELECT n FROM BudgetAlertNotification n WHERE n.userId = :userId ORDER BY n.sentAt ASC, n.id ASC")
    List<BudgetAlertNotification> findByUserId(@Param("userId") UUID userId);

    /** Hard-delete the user's rows (LGPD account purge). Must run BEFORE deleting the categories and the user row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM budget_alert_notifications WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
