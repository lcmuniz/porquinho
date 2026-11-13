package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.DashboardLayer1Response;
import com.porquinho.dto.UpdateAccountRequest;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 7.4: the two per-user queries of the wealth curve against the real schema (PostgreSQL via Testcontainers):
 * the last snapshot before the cutoff per account (DISTINCT ON), removed accounts included, another user's snapshots
 * left out, and the overview ending on the total balance. Not @Transactional; cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardWealthIntegrationTest {

    @Autowired
    private AccountBalanceHistoryRepository repository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DashboardCache dashboardCache;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            dashboardCache.evictAll(id);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
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
        return accountService.create(owner, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal(balance)))
            .getId();
    }

    private void edit(UUID accountId, String balance) {
        long version = jdbc.queryForObject("SELECT version FROM accounts WHERE id = ?", Long.class, accountId);
        accountService.update(userId, accountId, new UpdateAccountRequest("Conta", null, "CHECKING",
            new BigDecimal(balance), version));
    }

    private void moveHistoryBack(UUID accountId, int days) {
        jdbc.update("UPDATE account_balance_history SET snapshot_date = snapshot_date - make_interval(days => ?) "
            + "WHERE account_id = ?", days, accountId);
    }

    @Test
    void lastSnapshotBeforeTheCutoffPerAccountAndTheRestSinceIt() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        edit(nubank, "1100.00");
        moveHistoryBack(nubank, 400);
        edit(nubank, "1300.00");
        UUID inter = add(userId, "Inter", "50.00");
        moveHistoryBack(inter, 500);
        UUID removed = add(userId, "Removida", "70.00");
        moveHistoryBack(removed, 450);
        accountService.delete(userId, removed, "127.0.0.1");
        UUID theirs = add(newUser(), "Deles", "999.00");
        moveHistoryBack(theirs, 450);

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(365);
        List<AccountBalanceHistory> before = repository.findLastPerAccountBefore(userId, cutoff);
        List<AccountBalanceHistory> since = repository.findByUserSince(userId, cutoff);

        assertThat(before).extracting(h -> h.getBalanceSnapshot().toPlainString())
            .containsExactlyInAnyOrder("1100.00", "50.00", "70.00");
        assertThat(since).extracting(h -> h.getBalanceSnapshot().toPlainString()).containsExactly("1300.00");
    }

    @Test
    void theOverviewEndsOnTheTotalBalanceAndLeavesOtherUsersOut() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        moveHistoryBack(nubank, 30);
        edit(nubank, "1500.00");
        UUID theirs = add(newUser(), "Deles", "999.00");
        moveHistoryBack(theirs, 30);

        List<DashboardLayer1Response.WealthPoint> points = dashboardService.overview(userId).wealthHistory();

        assertThat(points).extracting(p -> p.balance().stripTrailingZeros().toPlainString())
            .containsExactly("0", "1000", "1500");
    }

    @Test
    void twoEditsOnTheSameDayMakeOnePointWithTheSecondBalance() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        moveHistoryBack(nubank, 100);
        edit(nubank, "1200.00");
        edit(nubank, "900.00");
        // moves the initial snapshot to 110 days ago and both edits to the same day, 10 days ago
        moveHistoryBack(nubank, 10);
        edit(nubank, "700.00");

        List<DashboardLayer1Response.WealthPoint> points = dashboardService.overview(userId).wealthHistory();

        assertThat(points).hasSize(4);
        assertThat(points.get(2).balance()).isEqualByComparingTo("900.00");
        assertThat(points.get(3).balance()).isEqualByComparingTo("700.00");
    }
}
