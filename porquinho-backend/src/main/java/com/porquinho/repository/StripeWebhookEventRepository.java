package com.porquinho.repository;

import com.porquinho.entity.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Dedup of the Stripe webhook (pre-10.4): ids of the events already processed. */
@Repository
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {

    /** Records the event as processed; 1 when this call inserted the row, 0 when it already existed. */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO stripe_webhook_events (event_id, event_type, processed_at) "
        + "VALUES (:eventId, :eventType, NOW()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType);
}
