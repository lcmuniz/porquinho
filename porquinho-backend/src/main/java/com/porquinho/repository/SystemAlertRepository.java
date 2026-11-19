package com.porquinho.repository;

import com.porquinho.entity.SystemAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Story 10.5: the alerts of the critical metrics. Rows are never deleted. */
@Repository
public interface SystemAlertRepository extends JpaRepository<SystemAlert, Long> {

    /** The last alert of the key (the 1 h dedup). */
    Optional<SystemAlert> findFirstByAlertKeyOrderByTriggeredAtDesc(String alertKey);

    /** The open episodes (not normalized yet), whatever the admin did. */
    List<SystemAlert> findByNormalizedAtIsNull();

    /**
     * The 100 of the Alertas tab: the unresolved first, so every alert in the open count can be resolved, then the
     * most recent.
     */
    @Query(value = "SELECT * FROM system_alerts ORDER BY (resolved_at IS NOT NULL), triggered_at DESC, id DESC LIMIT 100",
        nativeQuery = true)
    List<SystemAlert> findTop100UnresolvedFirst();

    /** Alerts not resolved by an admin yet. */
    long countByResolvedAtIsNull();

    /**
     * Opens an episode; 1 when this call inserted the row, 0 when the key already has one open. Atomic on the partial
     * UNIQUE (alert_key) WHERE normalized_at IS NULL: two runs never both get 1.
     */
    @Modifying
    @Query(value = "INSERT INTO system_alerts (alert_key, alert_type, bank, metric_value, threshold, triggered_at) "
        + "VALUES (:key, :type, :bank, :value, :threshold, :at) "
        + "ON CONFLICT (alert_key) WHERE normalized_at IS NULL DO NOTHING", nativeQuery = true)
    int insertIfNoOpenEpisode(@Param("key") String key, @Param("type") String type, @Param("bank") String bank,
                              @Param("value") BigDecimal value, @Param("threshold") BigDecimal threshold,
                              @Param("at") OffsetDateTime at);

    /** Ends the episode of the key (the metric is back to normal). */
    @Transactional
    @Modifying
    @Query(value = "UPDATE system_alerts SET normalized_at = :at WHERE alert_key = :key AND normalized_at IS NULL",
        nativeQuery = true)
    int normalize(@Param("key") String key, @Param("at") OffsetDateTime at);

    /** First resolve wins: a second one keeps the original resolved_at and resolved_by. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE system_alerts SET resolved_at = :at, resolved_by = :by WHERE id = :id AND resolved_at IS NULL",
        nativeQuery = true)
    int resolve(@Param("id") long id, @Param("at") OffsetDateTime at, @Param("by") UUID by);
}
