package com.porquinho.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * A Stripe webhook event already processed (pre-10.4, V47). Written only through
 * {@code StripeWebhookEventRepository.insertIfAbsent}, after the event was handled with success.
 */
@Entity
@Table(name = "stripe_webhook_events")
public class StripeWebhookEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    public StripeWebhookEvent() {
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
