package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.service.EmailService.BudgetAlertLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pre-Epic 10 through the real Spring proxies, the real candidate query and PostgreSQL: the dedup is atomic (two
 * concurrent runs send one e-mail), a second run sends nothing and the preference off sends nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class BudgetAlertServiceIntegrationTest {

    @Autowired
    private BudgetAlertService budgetAlertService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private UserPreferencesService userPreferencesService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EmailService emailService;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final LocalDate month = YearMonth.now(ZoneId.of("America/Sao_Paulo")).atDay(1);

    @BeforeEach
    void smtpConfigured() {
        when(emailService.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM budget_alert_notifications WHERE user_id = ?", id);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM budget_allocations WHERE user_id = ?", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", id);
            jdbc.update("DELETE FROM categories WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void overspentCategoryAlertsOnceWithHundredAndRecordsBothLevels() {
        String email = "alerta-" + UUID.randomUUID() + "@example.com";
        UUID userId = userWithSpending(email, 130);

        budgetAlertService.runAlerts();
        budgetAlertService.runAlerts();

        ArgumentCaptor<List<BudgetAlertLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(emailService, times(1)).sendBudgetAlert(eq(email), captor.capture(), endsWith("/orcamento"),
            endsWith("/dashboard"), contains("/api/v1/email/unsubscribe?token=" + userId + "."));
        assertThat(captor.getValue()).singleElement().extracting(BudgetAlertLine::level).isEqualTo(100);
        assertThat(jdbc.queryForList("SELECT level FROM budget_alert_notifications WHERE user_id = ? ORDER BY level",
            Integer.class, userId)).containsExactly(90, 100);
    }

    @Test
    void concurrentRunsSendOneEmail() throws Exception {
        String email = "concorrente-" + UUID.randomUUID() + "@example.com";
        UUID userId = userWithSpending(email, 95);

        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> runs = List.of(1, 2).stream().map(i -> CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return budgetAlertService.processUser(userId);
        })).toList();
        start.countDown();

        assertThat(runs.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count()).isEqualTo(1);
        verify(emailService, times(1)).sendBudgetAlert(eq(email), anyList(), anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_alert_notifications WHERE user_id = ?",
            Long.class, userId)).isEqualTo(1);
    }

    @Test
    void marksOfThePreviousMonthDoNotBlockTheCurrentOne() {
        String email = "mes-novo-" + UUID.randomUUID() + "@example.com";
        UUID userId = userWithSpending(email, 92);
        UUID category = jdbc.queryForObject(
            "SELECT category_id FROM budget_allocations WHERE user_id = ?", UUID.class, userId);
        for (int level : List.of(90, 100)) {
            jdbc.update("INSERT INTO budget_alert_notifications (user_id, category_id, month, level) VALUES (?, ?, ?, ?)",
                userId, category, month.minusMonths(1), level);
        }

        assertThat(budgetAlertService.processUser(userId)).isTrue();

        verify(emailService, times(1)).sendBudgetAlert(eq(email), anyList(), anyString(), anyString(), anyString());
        assertThat(jdbc.queryForList("SELECT level FROM budget_alert_notifications WHERE user_id = ? AND month = ?",
            Integer.class, userId, month)).containsExactly(90);
    }

    @Test
    void newUserHasThePreferenceOnAndCanTurnItOff() {
        UUID userId = UUID.randomUUID();
        users.add(userId);
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            "novo-" + userId + "@example.com");

        assertThat(userPreferencesService.get(userId).budgetAlertEmail()).isTrue();
        userPreferencesService.update(userId, new com.porquinho.dto.UserPreferences(false));
        assertThat(jdbc.queryForObject("SELECT budget_alert_email_enabled FROM users WHERE id = ?", Boolean.class,
            userId)).isFalse();
    }

    @Test
    void preferenceOffSendsNothing() {
        String email = "desligado-" + UUID.randomUUID() + "@example.com";
        UUID userId = userWithSpending(email, 130);
        jdbc.update("UPDATE users SET budget_alert_email_enabled = false WHERE id = ?", userId);

        budgetAlertService.runAlerts();

        verify(emailService, never()).sendBudgetAlert(eq(email), anyList(), anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM budget_alert_notifications WHERE user_id = ?",
            Long.class, userId)).isZero();
    }

    /** Trial user with access, a child category with 100 allocated this month and {@code spent} confirmed debits. */
    private UUID userWithSpending(String email, int spent) {
        UUID userId = UUID.randomUUID();
        users.add(userId);
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, email);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        subscriptionRepository.save(new Subscription(userId, PlanType.TRIAL, Status.ACTIVE, now.minusDays(1),
            now.plusDays(29)));
        UUID account = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Conta', 'CHECKING', 0, 0)", account, userId);
        UUID parent = category(userId, "Casa", null);
        UUID food = category(userId, "Mercado", parent);
        jdbc.update("INSERT INTO budget_allocations (user_id, category_id, month_year, allocated_amount) "
            + "VALUES (?, ?, ?, 100)", userId, food, month);
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_installment, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, 'x', 'x', ?, 'DEBIT', 'REGULAR', 'CONFIRMED', false, false)",
            UUID.randomUUID(), userId, account, food, month, spent);
        return userId;
    }

    private UUID category(UUID userId, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }
}
