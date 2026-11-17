package com.porquinho.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pre-10.4: the native ON CONFLICT insert of the webhook dedup against the real PostgreSQL (Testcontainers). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StripeWebhookEventRepositoryIntegrationTest {

    @Autowired
    private StripeWebhookEventRepository repository;

    @Test
    void insertingTheSameIdTwiceReturnsZeroTheSecondTimeWithoutException() {
        String eventId = "evt_" + UUID.randomUUID();

        assertThat(repository.existsById(eventId)).isFalse();
        assertThat(repository.insertIfAbsent(eventId, "invoice.paid")).isEqualTo(1);
        assertThat(repository.insertIfAbsent(eventId, "invoice.paid")).isZero();
        assertThat(repository.existsById(eventId)).isTrue();
    }
}
