package com.porquinho.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Story 10.1: system health for the admin panel. Aggregates only, never a user's financial data. Each block carries
 * its color; UNKNOWN means "sem dados" (no snapshot or no request yet).
 */
public record AdminHealthResponse(
    OffsetDateTime generatedAt,
    Uptime uptime,
    Errors errors,
    ResponseTime responseTime,
    List<Endpoint> endpoints,
    List<HourPoint> hourly,
    Database database,
    Redis redis,
    Sessions sessions
) {

    public enum Status { GREEN, YELLOW, RED, UNKNOWN }

    /** Percentage of the minutes with a snapshot; null without snapshots. The color follows the 30 days. */
    public record Uptime(Double last24h, Double last30d, double target, Status status) {
    }

    /** 5xx answers of the API; {@code errorRate24h} in % (null without requests in 24h). */
    public record Errors(long last24h, long last7d, long last30d, Double errorRate24h, Status status) {
    }

    /** Average response time of the last 24h in ms (null without requests). */
    public record ResponseTime(Double avgMs24h, Status status) {
    }

    /** Since the last start of the backend (Micrometer timer), the slowest first. */
    public record Endpoint(String method, String uri, long count, double avgMs, long errors) {
    }

    /** One hour of the last 24h (start of the hour, UTC); {@code avgMs} null without requests. */
    public record HourPoint(OffsetDateTime hour, long requests, long errors, Double avgMs) {
    }

    /** Hikari pool and the queries of this database active for more than 1 s right now. */
    public record Database(int active, int idle, int max, int waiting, Status poolStatus,
                           long slowQueries, Status slowQueriesStatus) {
    }

    /** From Redis {@code INFO}; {@code up=false} when it failed, the other fields then null. */
    public record Redis(boolean up, Double hitRate, Long usedMemoryBytes, Long maxMemoryBytes, Status status) {
    }

    /** Sessions not revoked, not expired and with activity in the last 30 min. */
    public record Sessions(long active, Status status) {
    }
}
