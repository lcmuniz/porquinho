package com.porquinho.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Story 10.1: the API requests of one minute (delta of http.server.requests, /actuator left out). Kept for 31 days. */
@Entity
@Table(name = "system_health_snapshots")
public class SystemHealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "total_time_ms", nullable = false)
    private double totalTimeMs;

    public SystemHealthSnapshot() {
    }

    public SystemHealthSnapshot(OffsetDateTime recordedAt, long requestCount, long errorCount, double totalTimeMs) {
        this.recordedAt = recordedAt;
        this.requestCount = requestCount;
        this.errorCount = errorCount;
        this.totalTimeMs = totalTimeMs;
    }

    public Long getId() { return id; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public long getRequestCount() { return requestCount; }
    public long getErrorCount() { return errorCount; }
    public double getTotalTimeMs() { return totalTimeMs; }
}
