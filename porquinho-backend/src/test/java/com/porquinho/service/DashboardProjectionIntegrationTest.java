package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.TransactionCreateRequest;
import com.porquinho.repository.TransactionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Retro Epic 7 A1 (B1, B2): the layer 1 projection against the real schema (PostgreSQL via Testcontainers) and the real
 * balance rule (decision D3). An account debit dated later in the month is already in the balance and must not be
 * subtracted again; a card debit dated later and the month's future installments are; a removed card's are not.
 * Uses the real clock, so it is skipped on the last day of the month (no day left after today). Not @Transactional.
 */
@SpringBootTest
@ActiveProfiles("test")
class DashboardProjectionIntegrationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private DashboardCache dashboardCache;
    @Autowired
    private BudgetBalanceCache balanceCache;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        dashboardCache.evictAll(userId);
        balanceCache.evict(userId);
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM credit_cards WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_classification_patterns WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private UUID category(String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }

    private UUID card(boolean removed) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO credit_cards (id, user_id, card_name, card_limit, due_day, deleted_at, deleted_by) "
            + "VALUES (?, ?, 'Roxinho', 0, 10, " + (removed ? "now(), ?" : "NULL, ?") + ")", id, userId,
            removed ? userId : null);
        return id;
    }

    private void cardRow(UUID cardId, UUID categoryId, LocalDate date, String amount, boolean future) {
        jdbc.update("INSERT INTO transactions (id, user_id, credit_card_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, 'Compra', 'Compra', ?, 'DEBIT', 'REGULAR', 'CONFIRMED', ?)", UUID.randomUUID(),
            userId, cardId, categoryId, date, new BigDecimal(amount), future);
    }

    @Test
    void anAccountDebitDatedLaterInTheMonthIsOnlyInTheBalance() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate last = today.withDayOfMonth(today.lengthOfMonth());
        assumeTrue(today.isBefore(last), "no day left in the month after today");
        LocalDate tomorrow = today.plusDays(1);
        UUID food = category("Mercado", category("Essenciais", null));
        UUID account = accountService.create(userId,
            new CreateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("3000.00"))).getId();

        transactionService.create(userId, new TransactionCreateRequest(tomorrow, "Aluguel", new BigDecimal("100.00"),
            "DEBIT", food, account, null), "127.0.0.1");
        cardRow(card(false), null, tomorrow, "50.00", false);
        cardRow(card(false), food, last, "30.00", true);
        cardRow(card(true), food, tomorrow, "999.00", false);
        dashboardCache.evictAll(userId);

        assertThat(jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, account))
            .isEqualByComparingTo("2900.00");
        assertThat(transactionRepository.sumCardDebits(userId, tomorrow, last)).isEqualByComparingTo("50.00");
        // 2900 (the account debit already in it) - 0 pace - 50 (card, tomorrow, no category) - 30 (future installment) = 2820
        assertThat(dashboardService.overview(userId).projectedBalance()).isEqualByComparingTo("2820.00");
    }
}
