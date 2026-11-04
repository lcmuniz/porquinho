package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Retro do Epic 4 (X1): optimistic version of the account against the real schema (PostgreSQL via Testcontainers,
 * Flyway V24). An edit form opened before a transfer or a reconciliation gets 409 on save, and the transfer or
 * reconciliation is kept, with no "edit" row in the history. Every change of the account increments the version.
 * Not @Transactional: each service call commits on its own, like two browser tabs. Cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountVersionIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        users.add(userId);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM reconciliation_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transfers WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID add(String name, String balance) {
        return accountService.create(userId, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal(balance))).getId();
    }

    private long version(UUID accountId) {
        return jdbc.queryForObject("SELECT version FROM accounts WHERE id = ?", Long.class, accountId);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private List<String> events(UUID accountId) {
        return jdbc.queryForList("SELECT created_by_event FROM account_balance_history WHERE account_id = ? "
            + "ORDER BY snapshot_date, created_at", String.class, accountId);
    }

    private void transfer(UUID from, UUID to, String amount) {
        transferService.create(userId, new CreateTransferRequest(UUID.randomUUID(), from, to, new BigDecimal(amount),
            LocalDate.now(ZoneId.of("America/Sao_Paulo")), null), "127.0.0.1");
    }

    @Test
    void editingWithTheCurrentVersionSucceedsAndIncrementsIt() {
        Account created = accountService.create(userId,
            new CreateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("100.00")));
        UUID nubank = created.getId();
        assertThat(created.getVersion()).isZero();
        assertThat(version(nubank)).isZero();

        accountService.update(userId, nubank,
            new UpdateAccountRequest("Nubank PJ", null, "CHECKING", new BigDecimal("250.00"), 0L));

        assertThat(version(nubank)).isEqualTo(1);
        assertThat(balance(nubank)).isEqualByComparingTo("250.00");
        assertThat(events(nubank)).containsExactly("initial", "edit");
    }

    @Test
    void aTransferBetweenOpeningAndSavingTheEditIsAConflictAndIsKept() {
        // Critério de aceite: edição aberta no Nubank, transferência de R$ 200,00 em outra aba, salvar a edição
        UUID nubank = add("Nubank", "1000.00");
        UUID inter = add("Inter", "0.00");
        long opened = version(nubank);

        transfer(nubank, inter, "200.00");

        assertThatThrownBy(() -> accountService.update(userId, nubank,
            new UpdateAccountRequest("Nubank PJ", null, "CHECKING", new BigDecimal("1000.00"), opened)))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Esta conta foi alterada em outra tela. Abra a edição de novo.");

        assertThat(balance(nubank)).isEqualByComparingTo("800.00");
        assertThat(jdbc.queryForObject("SELECT account_name FROM accounts WHERE id = ?", String.class, nubank))
            .isEqualTo("Nubank");
        assertThat(events(nubank)).containsExactly("initial", "transfer");
        assertThat(version(nubank)).isEqualTo(opened + 1);
    }

    @Test
    void aReconciliationBetweenOpeningAndSavingTheEditIsAConflictAndIsKept() {
        UUID nubank = add("Nubank", "1000.00");
        long opened = version(nubank);

        reconciliationService.reconcile(userId, nubank, new CreateReconciliationRequest(new BigDecimal("1050.00"), null),
            "127.0.0.1");

        assertThatThrownBy(() -> accountService.update(userId, nubank,
            new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("1000.00"), opened)))
            .isInstanceOf(ConflictException.class);

        assertThat(balance(nubank)).isEqualByComparingTo("1050.00");
        assertThat(events(nubank)).containsExactly("initial", "reconciliation");
    }

    @Test
    void twoConcurrentEditsWithTheSameVersionLeaveOneSavedAndOneConflict() throws Exception {
        UUID nubank = add("Nubank", "100.00");
        List<String> balances = List.of("200.00", "300.00");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes;
        try {
            List<Future<String>> results = balances.stream().map(b -> pool.submit(() -> {
                start.await();
                try {
                    accountService.update(userId, nubank,
                        new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal(b), 0L));
                    return "200";
                } catch (ConflictException e) {
                    return "409";
                }
            })).toList();
            start.countDown();
            outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).containsExactlyInAnyOrder("200", "409");
        assertThat(version(nubank)).isEqualTo(1);
        assertThat(events(nubank)).containsExactly("initial", "edit");
        assertThat(balance(nubank).toPlainString()).isIn("200.00", "300.00");
    }

    @Test
    void everyChangeOfTheAccountIncrementsTheVersion() {
        UUID nubank = add("Nubank", "1000.00");
        UUID inter = add("Inter", "0.00");
        assertThat(version(nubank)).isZero();

        transfer(nubank, inter, "100.00");
        assertThat(version(nubank)).isEqualTo(1);
        assertThat(version(inter)).isEqualTo(1);

        reconciliationService.reconcile(userId, nubank, new CreateReconciliationRequest(new BigDecimal("950.00"), null),
            "127.0.0.1");
        assertThat(version(nubank)).isEqualTo(2);

        accountService.delete(userId, nubank, "127.0.0.1");
        assertThat(version(nubank)).isEqualTo(3);

        accountService.restore(userId, nubank, "127.0.0.1");
        assertThat(version(nubank)).isEqualTo(4);

        accountService.update(userId, nubank, new UpdateAccountRequest("Nubank PJ", null, "CHECKING", new BigDecimal("950.00"), 4L));
        assertThat(version(nubank)).isEqualTo(5);
    }
}
