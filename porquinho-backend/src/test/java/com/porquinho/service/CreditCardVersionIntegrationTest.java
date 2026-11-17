package com.porquinho.service;

import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.dto.UpdateCreditCardRequest;
import com.porquinho.entity.CreditCard;
import com.porquinho.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Triagem 16/11, grupo B: optimistic version of the credit card against the real schema (Flyway V48), like
 * AccountVersionIntegrationTest. An edit saved from a stale form gets 409 and the other tab's save is kept.
 * Not @Transactional: each service call commits on its own, like two browser tabs. Cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditCardVersionIntegrationTest {

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private UUID add(String name) {
        return creditCardService.create(userId,
            new CreateCreditCardRequest(name, null, new BigDecimal("5000.00"), new BigDecimal("10"))).getId();
    }

    private long version(UUID cardId) {
        return jdbc.queryForObject("SELECT version FROM credit_cards WHERE id = ?", Long.class, cardId);
    }

    private UpdateCreditCardRequest edit(String name, String limit, long version) {
        return new UpdateCreditCardRequest(name, null, new BigDecimal(limit), new BigDecimal("10"), version);
    }

    @Test
    void editingWithTheCurrentVersionSucceedsAndIncrementsIt() {
        CreditCard created = creditCardService.create(userId,
            new CreateCreditCardRequest("Nubank", null, new BigDecimal("5000.00"), new BigDecimal("10")));
        assertThat(created.getVersion()).isZero();

        CreditCard updated = creditCardService.update(userId, created.getId(), edit("Nubank PJ", "6000.00", 0L));

        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(version(created.getId())).isEqualTo(1);
    }

    @Test
    void aSaveFromAStaleFormIsAConflictAndKeepsTheOtherTabsSave() {
        // Critério de aceite: duas abas abrem a edição; a primeira salva, a segunda recebe o aviso
        UUID card = add("Nubank");
        long opened = version(card);

        creditCardService.update(userId, card, edit("Nubank Aba 1", "6000.00", opened));

        assertThatThrownBy(() -> creditCardService.update(userId, card, edit("Nubank Aba 2", "7000.00", opened)))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Este cartão foi alterado em outra tela. Abra a edição de novo.");

        assertThat(jdbc.queryForObject("SELECT card_name FROM credit_cards WHERE id = ?", String.class, card))
            .isEqualTo("Nubank Aba 1");
        assertThat(version(card)).isEqualTo(opened + 1);
    }

    @Test
    void twoConcurrentEditsWithTheSameVersionLeaveOneSavedAndOneConflict() throws Exception {
        UUID card = add("Nubank");
        List<String> limits = List.of("6000.00", "7000.00");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes = new ArrayList<>();
        try {
            List<Future<String>> results = limits.stream().map(l -> pool.submit(() -> {
                start.await();
                try {
                    creditCardService.update(userId, card, edit("Nubank", l, 0L));
                    return "200";
                } catch (ConflictException e) {
                    return "409";
                }
            })).toList();
            start.countDown();
            for (Future<String> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).containsExactlyInAnyOrder("200", "409");
        assertThat(version(card)).isEqualTo(1);
    }

    @Test
    void removingAndRestoringIncrementTheVersionWithoutSendingIt() {
        UUID card = add("Nubank");

        creditCardService.delete(userId, card, "127.0.0.1");
        creditCardService.restore(userId, card, "127.0.0.1");

        assertThat(version(card)).isEqualTo(2);
    }
}
