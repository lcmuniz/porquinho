package com.porquinho.service;

import com.porquinho.dto.BalanceHistoryResponse;
import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.CreateTransferRequest;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 4.7: balance history against the real schema (PostgreSQL via Testcontainers): the four events in order, the
 * period with the point before it, another user's account, a removed account and 1000 points in one response.
 * Not @Transactional: each service call commits on its own. Cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class BalanceHistoryIntegrationTest {

    @Autowired
    private BalanceHistoryService balanceHistoryService;

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
        userId = newUser();
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

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID add(UUID owner, String name, String balance) {
        return accountService.create(owner, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal(balance))).getId();
    }

    private void moveHistoryBack(UUID accountId, long days) {
        jdbc.update("UPDATE account_balance_history SET snapshot_date = snapshot_date - make_interval(days => ?) "
            + "WHERE account_id = ?", (int) days, accountId);
    }

    @Test
    void initialEditTransferAndReconciliationComeInOrder() {
        // Critério de aceite: Nubank 1.000,00 → edição 1.200,00 → transferência de 200,00 → reconciliação 1.050,00
        UUID nubank = add(userId, "Nubank", "1000.00");
        UUID inter = add(userId, "Inter", "0.00");
        accountService.update(userId, nubank, new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("1200.00")));
        transferService.create(userId, new CreateTransferRequest(UUID.randomUUID(), nubank, inter, new BigDecimal("200.00"),
            LocalDate.now(ZoneId.of("America/Sao_Paulo")), null), "127.0.0.1");
        reconciliationService.reconcile(userId, nubank, new CreateReconciliationRequest(new BigDecimal("1050.00"), null),
            "127.0.0.1");

        BalanceHistoryResponse response = balanceHistoryService.history(userId, nubank, "3m");

        assertThat(response.account().accountName()).isEqualTo("Nubank");
        assertThat(response.account().currentBalance()).isEqualByComparingTo("1050.00");
        assertThat(response.points()).extracting(BalanceHistoryResponse.Point::event)
            .containsExactly("initial", "edit", "transfer", "reconciliation");
        assertThat(response.points()).extracting(p -> p.balance().toPlainString())
            .containsExactly("1000.00", "1200.00", "1000.00", "1050.00");
    }

    @Test
    void thirtyDaysWithoutRecentEventsShowsTheLastKnownBalanceOnly() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        accountService.update(userId, nubank, new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("-50.00")));
        moveHistoryBack(nubank, 60);

        BalanceHistoryResponse response = balanceHistoryService.history(userId, nubank, "30d");

        assertThat(response.points()).hasSize(1);
        assertThat(response.periodStart()).isBetween(Instant.now().minus(31, ChronoUnit.DAYS),
            Instant.now().minus(29, ChronoUnit.DAYS));
        // O ponto anterior mantém a data real; o gráfico é que o prende ao início do período
        assertThat(response.points().get(0).date().toInstant()).isBefore(response.periodStart());
        assertThat(response.points().get(0).event()).isEqualTo("edit");
        assertThat(response.points().get(0).balance()).isEqualByComparingTo("-50.00");
        // O mesmo histórico inteiro no período de 3 meses
        assertThat(balanceHistoryService.history(userId, nubank, "3m").points()).hasSize(2);
    }

    @Test
    void periodPointsComeAfterThePointBeforeThePeriod() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        moveHistoryBack(nubank, 400);
        accountService.update(userId, nubank, new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("1100.00")));
        moveHistoryBack(nubank, 100);
        accountService.update(userId, nubank, new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("1300.00")));

        // initial há 500 dias, 1100 há 100 dias, 1300 hoje
        assertThat(balanceHistoryService.history(userId, nubank, "30d").points())
            .extracting(p -> p.balance().toPlainString()).containsExactly("1100.00", "1300.00");
        assertThat(balanceHistoryService.history(userId, nubank, "6m").points())
            .extracting(p -> p.balance().toPlainString()).containsExactly("1000.00", "1100.00", "1300.00");
        assertThat(balanceHistoryService.history(userId, nubank, "all").points()).hasSize(3);
    }

    @Test
    void anotherUsersAccountIs404() {
        UUID theirs = add(newUser(), "Deles", "10.00");
        assertThatThrownBy(() -> balanceHistoryService.history(userId, theirs, "3m"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Conta não encontrada");
    }

    @Test
    void invalidRangeIs400() {
        UUID nubank = add(userId, "Nubank", "10.00");
        assertThatThrownBy(() -> balanceHistoryService.history(userId, nubank, "abc"))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Período inválido");
    }

    @Test
    void removedAccountKeepsItsHistory() {
        UUID nubank = add(userId, "Nubank", "100.00");
        add(userId, "Inter", "100.00");
        accountService.delete(userId, nubank, "127.0.0.1");

        BalanceHistoryResponse response = balanceHistoryService.history(userId, nubank, "3m");

        assertThat(response.points()).extracting(BalanceHistoryResponse.Point::event).containsExactly("initial");
    }

    @Test
    void thousandPointsComeInOneOrderedResponse() {
        UUID nubank = add(userId, "Nubank", "0.00");
        jdbc.update("DELETE FROM account_balance_history WHERE account_id = ?", nubank);
        Instant start = Instant.now().minus(300, ChronoUnit.DAYS);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(new Object[]{nubank, userId, BigDecimal.valueOf(i), Timestamp.from(start.plus(i * 6L, ChronoUnit.HOURS))});
        }
        jdbc.batchUpdate("INSERT INTO account_balance_history (id, account_id, user_id, balance_snapshot, snapshot_date, "
            + "created_by_event, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, 'edit', now())", rows);

        long begin = System.nanoTime();
        BalanceHistoryResponse response = balanceHistoryService.history(userId, nubank, "1y");
        long millis = (System.nanoTime() - begin) / 1_000_000;

        assertThat(response.points()).hasSize(1000);
        assertThat(response.points().get(0).balance()).isEqualByComparingTo("0");
        assertThat(response.points().get(999).balance()).isEqualByComparingTo("999");
        assertThat(millis).isLessThan(1000);
    }
}
