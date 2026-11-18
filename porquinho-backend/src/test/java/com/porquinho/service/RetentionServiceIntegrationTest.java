package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pre-Epic 10 through the real Spring proxies, the real candidate query and PostgreSQL: the retention purge erases
 * every piece of the user's data (one assert per table), keeps the account_deletions row with the e-mail hash, and
 * the same e-mail registering again gets no trial (after the retention purge or after a deletion the user asked for).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class RetentionServiceIntegrationTest {

    @Autowired
    private RetentionService retentionService;
    @Autowired
    private AuthService authService;
    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EmailService emailService;
    @MockBean
    private SupabaseAdminClient supabaseAdminClient;
    @MockBean
    private StripeBillingService stripeBillingService;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final List<String> hashes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void smtpConfigured() {
        when(emailService.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
            jdbc.update("DELETE FROM account_deletions WHERE user_id = ?", id);
        }
        hashes.forEach(h -> jdbc.update("DELETE FROM account_deletions WHERE email_hash = ?", h));
    }

    @Test
    void purgeOnDayThirtyErasesEverythingAndTheEmailComesBackWithoutTrial() {
        String email = "retencao-" + UUID.randomUUID() + "@example.com";
        UUID userId = UUID.randomUUID();
        users.add(userId);
        hashes.add(AccountDeletion.hashEmail(email));
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, email);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Subscription s = new Subscription(userId, PlanType.TRIAL, Status.ACTIVE, now.minusDays(61), now.minusDays(31));
        s.setRetentionWarning7dSentAt(now.minusDays(8));
        s.setRetentionWarning1dSentAt(now.minusDays(2));
        subscriptionRepository.save(s);
        seedData(userId);

        retentionService.runRetention();

        Map<String, String> tables = Map.ofEntries(
            Map.entry("transactions", "regular"),
            Map.entry("transfers", "transfer"),
            Map.entry("transaction_imports", "import"),
            Map.entry("reconciliation_history", "reconciliation"),
            Map.entry("account_balance_history", "balance history"),
            Map.entry("budget_allocations", "allocation"),
            Map.entry("budget_reallocations", "reallocation"),
            Map.entry("budget_alert_notifications", "budget alert"),
            Map.entry("goals", "goal"),
            Map.entry("goal_allocations", "goal allocation"),
            Map.entry("accounts", "account"),
            Map.entry("categories", "category"),
            Map.entry("subscriptions", "subscription"),
            Map.entry("users", "user"));
        assertThat(count("SELECT count(*) FROM transactions WHERE user_id = ? AND is_installment", userId))
            .as("installments").isZero();
        tables.forEach((table, what) -> assertThat(count(
            "SELECT count(*) FROM " + table + " WHERE " + ("users".equals(table) ? "id" : "user_id") + " = ?", userId))
            .as(what).isZero());
        verify(supabaseAdminClient).deleteUser(userId);

        Map<String, Object> deletion = jdbc.queryForMap(
            "SELECT status, reason, email_hash FROM account_deletions WHERE user_id = ?", userId);
        assertThat(deletion.get("status")).isEqualTo("PURGED");
        assertThat(deletion.get("reason")).isEqualTo("RETENTION");
        assertThat(deletion.get("email_hash")).isEqualTo(AccountDeletion.hashEmail(email));

        // Same e-mail, new Supabase id: no access, so GET /subscription shows it and paid routes answer 402
        UUID returning = UUID.randomUUID();
        users.add(returning);
        authService.registerOrGetUserFromEmail(returning.toString(), email.toUpperCase(), "10.0.0.1");
        assertReturningWithoutTrial(returning);
    }

    @Test
    void emailOfAUserRequestedDeletionAlsoComesBackWithoutTrial() {
        String email = "pedido-" + UUID.randomUUID() + "@example.com";
        UUID oldId = UUID.randomUUID();
        users.add(oldId);
        hashes.add(AccountDeletion.hashEmail(email));
        jdbc.update("INSERT INTO account_deletions (user_id, status, scheduled_purge_at, purged_at, reason, email_hash) "
            + "VALUES (?, 'PURGED', now(), now(), 'USER_REQUEST', ?)", oldId, AccountDeletion.hashEmail(email));

        UUID returning = UUID.randomUUID();
        users.add(returning);
        authService.registerOrGetUserFromEmail(returning.toString(), email, "10.0.0.1");

        assertReturningWithoutTrial(returning);
    }

    @Test
    void realCandidateQueryPicksRowsByTheirEndOfAccess() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // (a) trial: purge in 7 days
        UUID trial = subscribed(PlanType.TRIAL, Status.ACTIVE, now.minusDays(23), null, null);
        // (b) MONTHLY CANCELLED without access_ended_at: counted from next_billing_date
        UUID cancelledDue = subscribed(PlanType.MONTHLY, Status.CANCELLED, null, now.minusDays(23), null);
        UUID cancelledNotYet = subscribed(PlanType.MONTHLY, Status.CANCELLED, null, now.minusDays(10), null);
        // (c) EXPIRED / SUSPENDED: access_ended_at wins over the older trial/billing dates
        UUID expiredDue = subscribed(PlanType.TRIAL, Status.EXPIRED, now.minusDays(90), null, now.minusDays(23));
        UUID suspendedNotYet = subscribed(PlanType.MONTHLY, Status.SUSPENDED, null, now.minusDays(90), now.minusDays(10));
        // Always with access: never touched, whatever the dates
        UUID paymentFailed = subscribed(PlanType.MONTHLY, Status.PAYMENT_FAILED, null, now.minusDays(90), null);
        UUID monthlyActive = subscribed(PlanType.MONTHLY, Status.ACTIVE, null, now.minusDays(90), null);

        retentionService.runRetention();

        for (UUID warned : List.of(trial, cancelledDue, expiredDue)) {
            Subscription s = subscriptionRepository.findByUserId(warned).orElseThrow();
            assertThat(s.getRetentionWarning7dSentAt()).as("7d warning for %s", warned).isNotNull();
            assertThat(s.getRetentionWarning1dSentAt()).isNull();
        }
        for (UUID untouched : List.of(cancelledNotYet, suspendedNotYet, paymentFailed, monthlyActive)) {
            Subscription s = subscriptionRepository.findByUserId(untouched).orElseThrow();
            assertThat(s.getRetentionWarning7dSentAt()).as("no warning for %s", untouched).isNull();
            assertThat(s.getRetentionWarning1dSentAt()).isNull();
        }
    }

    private UUID subscribed(PlanType plan, Status status, OffsetDateTime trialEnd, OffsetDateTime nextBilling,
                            OffsetDateTime accessEndedAt) {
        UUID userId = UUID.randomUUID();
        users.add(userId);
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            "candidato-" + userId + "@example.com");
        Subscription s = new Subscription(userId, plan, status, trialEnd == null ? null : trialEnd.minusDays(30), trialEnd);
        s.setNextBillingDate(nextBilling);
        s.setAccessEndedAt(accessEndedAt);
        subscriptionRepository.save(s);
        return userId;
    }

    private void assertReturningWithoutTrial(UUID userId) {
        Subscription again = subscriptionRepository.findByUserId(userId).orElseThrow();
        assertThat(again.getPlanType()).isEqualTo(PlanType.TRIAL);
        assertThat(again.getStatus()).isEqualTo(Status.EXPIRED);
        assertThat(again.getAccessEndedAt()).isNotNull();
        assertThat(subscriptionService.hasAccess(userId)).isFalse();
        assertThat(count("SELECT count(*) FROM audit_logs WHERE user_id = ? "
            + "AND event_type = 'trial_denied_returning_email'", userId)).isEqualTo(1);
    }

    private long count(String sql, UUID userId) {
        return jdbc.queryForObject(sql, Long.class, userId);
    }

    /** One row of each kind of data the purge must erase. */
    private void seedData(UUID userId) {
        UUID nubank = UUID.randomUUID();
        UUID bb = UUID.randomUUID();
        for (UUID account : List.of(nubank, bb)) {
            jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
                + "VALUES (?, ?, ?, 'CHECKING', 0, 0)", account, userId, "Conta " + account);
        }
        UUID parent = category(userId, "Casa", null);
        UUID food = category(userId, "Mercado", parent);
        UUID rent = category(userId, "Aluguel", parent);

        UUID importId = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, status) "
            + "VALUES (?, ?, ?, 'x.ofx', 'OFX', 1, 'COMPLETED')", importId, userId, nubank);
        String insertTx = "INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, import_id, is_installment, "
            + "installment_number, installment_total, installment_group_id, is_future_installment) "
            + "VALUES (?, ?, ?, ?, CURRENT_DATE, 'x', 'x', 10, 'DEBIT', 'REGULAR', 'CONFIRMED', ?, ?, ?, ?, ?, ?)";
        jdbc.update(insertTx, UUID.randomUUID(), userId, nubank, food, importId, false, null, null, null, false);
        UUID group = UUID.randomUUID();
        jdbc.update(insertTx, UUID.randomUUID(), userId, nubank, food, null, true, 1, 3, group, false);
        jdbc.update(insertTx, UUID.randomUUID(), userId, nubank, food, null, true, 2, 3, group, true);

        jdbc.update("INSERT INTO transfers (id, user_id, from_account_id, to_account_id, amount, transfer_date) "
            + "VALUES (?, ?, ?, ?, 100, CURRENT_DATE)", UUID.randomUUID(), userId, nubank, bb);
        jdbc.update("INSERT INTO reconciliation_history (account_id, user_id, previous_balance, bank_balance, difference, "
            + "reconciled_at) VALUES (?, ?, 0, 5, 5, now())", nubank, userId);
        jdbc.update("INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, "
            + "created_by_event, created_at) VALUES (?, ?, 5, CURRENT_DATE, 'edit', now())", nubank, userId);
        jdbc.update("INSERT INTO budget_allocations (user_id, category_id, month_year, allocated_amount) "
            + "VALUES (?, ?, date_trunc('month', CURRENT_DATE)::date, 500)", userId, food);
        jdbc.update("INSERT INTO budget_reallocations (user_id, from_category_id, to_category_id, month_year, amount) "
            + "VALUES (?, ?, ?, date_trunc('month', CURRENT_DATE)::date, 50)", userId, food, rent);
        jdbc.update("INSERT INTO budget_alert_notifications (user_id, category_id, month, level) "
            + "VALUES (?, ?, date_trunc('month', CURRENT_DATE)::date, 90)", userId, food);
        UUID goal = UUID.randomUUID();
        jdbc.update("INSERT INTO goals (id, user_id, goal_name, target_amount, current_amount) "
            + "VALUES (?, ?, 'Viagem', 1000, 100)", goal, userId);
        jdbc.update("INSERT INTO goal_allocations (user_id, goal_id, month_year, allocated_amount, contributed_amount) "
            + "VALUES (?, ?, date_trunc('month', CURRENT_DATE)::date, 100, 100)", userId, goal);
    }

    private UUID category(UUID userId, String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }
}
