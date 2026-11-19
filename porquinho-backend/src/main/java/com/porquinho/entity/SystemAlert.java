package com.porquinho.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Story 10.5: one violation of a critical metric (V50). Never deleted; normalized ends the episode, resolved is the admin's. */
@Entity
@Table(name = "system_alerts")
public class SystemAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "alert_key", nullable = false)
    private String alertKey;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(name = "bank")
    private String bank;

    @Column(name = "metric_value", nullable = false)
    private BigDecimal metricValue;

    @Column(name = "threshold", nullable = false)
    private BigDecimal threshold;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "normalized_at")
    private OffsetDateTime normalizedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    public SystemAlert() {
    }

    public SystemAlert(String alertKey, String alertType, String bank, BigDecimal metricValue, BigDecimal threshold,
                       OffsetDateTime triggeredAt) {
        this.alertKey = alertKey;
        this.alertType = alertType;
        this.bank = bank;
        this.metricValue = metricValue;
        this.threshold = threshold;
        this.triggeredAt = triggeredAt;
    }

    public Long getId() { return id; }
    public String getAlertKey() { return alertKey; }
    public String getAlertType() { return alertType; }
    public String getBank() { return bank; }
    public BigDecimal getMetricValue() { return metricValue; }
    public BigDecimal getThreshold() { return threshold; }
    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public OffsetDateTime getNormalizedAt() { return normalizedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
}
