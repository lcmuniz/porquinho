package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.repository.UserMetricsRepository;
import com.porquinho.repository.UserMetricsRepository.Adoption;
import com.porquinho.repository.UserMetricsRepository.MonthCounts;
import com.porquinho.repository.UserMetricsRepository.Summary;
import com.porquinho.repository.UserMetricsRepository.WeekCounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 10.4 against the real database (Testcontainers + Flyway). The numbers are global and "now", so each test
 * compares the aggregates before and after its own rows: rows of other tests never change in between.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class UserMetricsIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserMetricsRepository repository;
    @Autowired
    private ObjectMapper json;

    private final List<UUID> users = new ArrayList<>();
    private UUID admin;
    private OffsetDateTime now;
    private ZonedDateTime weekStart;

    @BeforeEach
    void setUp() {
        admin = user(true, false);
        now = OffsetDateTime.now(ZoneOffset.UTC);
        weekStart = now.atZoneSameInstant(SAO_PAULO).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(SAO_PAULO);
    }

    @AfterEach
    void tearDown() {
        for (UUID u : users) {
            for (String table : new String[] {"user_sessions", "subscriptions", "onboarding_progress",
                    "transaction_imports", "reconciliation_history", "transfers", "budget_allocations", "categories",
                    "goals", "credit_cards", "accounts"}) {
                jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", u);
            }
            jdbc.update("DELETE FROM users WHERE id = ?", u);
        }
    }

    private UUID user(boolean isAdmin, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin, deleted_at) VALUES (?, ?, 'EMAIL', ?, "
            + (deleted ? "NOW()" : "NULL") + ")", id, id + "@example.com", isAdmin);
        users.add(id);
        return id;
    }

    private void session(UUID user, Duration createdAgo, Duration activeAgo) {
        jdbc.update("INSERT INTO user_sessions (user_id, jwt_token_hash, created_at, last_activity, expires_at) "
            + "VALUES (?, 'hash', ?, ?, ?)", user, now.minus(createdAgo), now.minus(activeAgo), now.plusDays(7));
    }

    /** A trial of 30 days ending at {@code trialEnd}. */
    private void subscription(UUID user, String plan, String status, OffsetDateTime trialEnd) {
        subscription(user, plan, status, trialEnd == null ? null : trialEnd.minusDays(30), trialEnd);
    }

    private void subscription(UUID user, String plan, String status, OffsetDateTime trialStart,
                              OffsetDateTime trialEnd) {
        jdbc.update("INSERT INTO subscriptions (user_id, plan_type, status, trial_start_date, trial_end_date) "
            + "VALUES (?, ?, ?, ?, ?)", user, plan, status, trialStart, trialEnd);
    }

    private void onboarding(UUID user, boolean completed) {
        jdbc.update("INSERT INTO onboarding_progress (user_id, is_completed) VALUES (?, ?)", user, completed);
    }

    private MonthCounts currentMonth() {
        String month = now.atZoneSameInstant(SAO_PAULO).toString().substring(0, 7);
        return repository.countByMonth(now).stream().filter(m -> m.getMonth().equals(month)).findFirst()
            .orElse(new MonthCounts() {
                public String getMonth() { return month; }
                public long getUsers() { return 0; }
                public long getPaid() { return 0; }
                public long getEligible() { return 0; }
            });
    }

    private UUID account(UUID user, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance, "
            + "deleted_at) VALUES (?, ?, 'Conta', 'CHECKING', 0, 0, " + (deleted ? "NOW()" : "NULL") + ")", id, user);
        return id;
    }

    private void completedImport(UUID user, UUID account, OffsetDateTime updatedAt) {
        jdbc.update("INSERT INTO transaction_imports (user_id, account_id, file_name, file_type, file_size_kb, status, "
            + "updated_at) VALUES (?, ?, 'extrato.ofx', 'OFX', 1, 'COMPLETED', ?)", user, account, updatedAt);
    }

    private Summary summary() {
        return repository.summary(now, weekStart.minusWeeks(4).toOffsetDateTime(), weekStart.toOffsetDateTime());
    }

    private List<WeekCounts> weeks() {
        return repository.activeByWeek(weekStart.minusWeeks(10).toOffsetDateTime(), now);
    }

    @Test
    void activeWindowsUseTheLastActivityOfTheSession() {
        Summary before = summary();
        session(user(false, false), Duration.ofDays(40), Duration.ofHours(2)); // old session, active now: all three
        session(user(false, false), Duration.ofDays(5), Duration.ofDays(3)); // 7 and 30 days
        session(user(false, false), Duration.ofDays(25), Duration.ofDays(20)); // 30 days only
        session(user(false, false), Duration.ofDays(45), Duration.ofDays(40)); // none
        UUID twice = user(false, false);
        session(twice, Duration.ofHours(5), Duration.ofHours(1));
        session(twice, Duration.ofHours(3), Duration.ofHours(1)); // two sessions, one user

        Summary after = summary();

        assertThat(after.getTotal() - before.getTotal()).isEqualTo(5);
        assertThat(after.getActive24h() - before.getActive24h()).isEqualTo(2);
        assertThat(after.getActive7d() - before.getActive7d()).isEqualTo(3);
        assertThat(after.getActive30d() - before.getActive30d()).isEqualTo(4);
    }

    @Test
    void weeklyActiveCountsTheSessionAliveAtTheEndOfEachWeek() {
        List<WeekCounts> before = weeks();
        // Alive from 3 days to 1 day before the end of a week: in its 7 days window, not in the next one
        OffsetDateTime end = weekStart.minusWeeks(5).toOffsetDateTime();
        Duration createdAgo = Duration.between(end.minusDays(3), now);
        Duration activeAgo = Duration.between(end.minusDays(1), now);
        session(user(false, false), createdAgo, activeAgo);

        List<WeekCounts> after = weeks();

        assertThat(after).hasSize(12);
        List<Long> weekly = new ArrayList<>();
        List<Long> monthly = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            weekly.add(after.get(i).getActive7d() - before.get(i).getActive7d());
            monthly.add(after.get(i).getActive30d() - before.get(i).getActive30d());
        }
        // Week 5 ends at weekStart − 5 weeks; the 30 days window keeps it in the next 4 weeks too
        assertThat(weekly).containsExactly(0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 0L);
        assertThat(monthly).containsExactly(0L, 0L, 0L, 0L, 0L, 1L, 1L, 1L, 1L, 1L, 0L, 0L);
    }

    @Test
    void deletedUserIsOutOfEverything() {
        Summary before = summary();
        Adoption adoptionBefore = repository.adoption();
        MonthCounts monthBefore = currentMonth();
        List<WeekCounts> weeksBefore = weeks();
        UUID gone = user(false, true);
        session(gone, Duration.ofHours(3), Duration.ofHours(1));
        subscription(gone, "MONTHLY", "ACTIVE", null);
        onboarding(gone, true);
        completedImport(gone, account(gone, false), weekStart.minusWeeks(2).toOffsetDateTime());
        subscription(user(false, true), "TRIAL", "ACTIVE", now.plusDays(10));
        subscription(user(false, true), "TRIAL", "EXPIRED", now.minusDays(1));

        Summary after = summary();
        MonthCounts monthAfter = currentMonth();

        assertThat(after.getTotal()).isEqualTo(before.getTotal());
        assertThat(after.getActive24h()).isEqualTo(before.getActive24h());
        assertThat(after.getPaid()).isEqualTo(before.getPaid());
        assertThat(after.getMonthly()).isEqualTo(before.getMonthly());
        assertThat(after.getOnboarded()).isEqualTo(before.getOnboarded());
        assertThat(after.getCheckins()).isEqualTo(before.getCheckins());
        assertThat(after.getTrials()).isEqualTo(before.getTrials());
        assertThat(after.getEndedTrials()).isEqualTo(before.getEndedTrials());
        assertThat(repository.adoption().getAccounts()).isEqualTo(adoptionBefore.getAccounts());
        assertThat(repository.adoption().getImports()).isEqualTo(adoptionBefore.getImports());
        assertThat(monthAfter.getUsers()).isEqualTo(monthBefore.getUsers());
        assertThat(monthAfter.getPaid()).isEqualTo(monthBefore.getPaid());
        assertThat(monthAfter.getEligible()).isEqualTo(monthBefore.getEligible());
        assertThat(weeks().get(11).getActive7d()).isEqualTo(weeksBefore.get(11).getActive7d());
        assertThat(weeks().get(11).getActive30d()).isEqualTo(weeksBefore.get(11).getActive30d());
    }

    @Test
    void lastWeekIsMeasuredNowNotAtTheEndOfTheWeek() {
        List<WeekCounts> before = weeks();
        // Active less than 7 days ago but before this Monday: out of the window ending next Monday, in the one of now
        OffsetDateTime last = weekStart.minusMinutes(1).toOffsetDateTime();
        session(user(false, false), Duration.between(last.minusHours(1), now), Duration.between(last, now));

        assertThat(weeks().get(11).getActive7d() - before.get(11).getActive7d()).isEqualTo(1);
    }

    @Test
    void subscriptionsCountTrialsPaidChurnAndTheEndedTrials() {
        Summary before = summary();
        subscription(user(false, false), "TRIAL", "ACTIVE", now.plusDays(10)); // trial
        subscription(user(false, false), "TRIAL", "ACTIVE", now.minusDays(1)); // ended: not a trial
        subscription(user(false, false), "MONTHLY", "ACTIVE", null);
        subscription(user(false, false), "MONTHLY", "CANCELLED", null);
        subscription(user(false, false), "MONTHLY", "EXPIRED", null); // cancelled, paid period over
        subscription(user(false, false), "MONTHLY", "SUSPENDED", null); // payment failed for good
        subscription(user(false, false), "MONTHLY", "PAYMENT_FAILED", null); // still retrying: not churn

        Summary after = summary();

        assertThat(after.getTrials() - before.getTrials()).isEqualTo(1);
        assertThat(after.getEndedTrials() - before.getEndedTrials()).isEqualTo(1);
        assertThat(after.getPaid() - before.getPaid()).isEqualTo(1);
        assertThat(after.getMonthly() - before.getMonthly()).isEqualTo(5);
        assertThat(after.getCancelled() - before.getCancelled()).isEqualTo(3);
    }

    @Test
    void checkinsAreDistinctUserWeeksOfSaoPaulo() {
        Summary before = summary();
        UUID u = user(false, false);
        session(u, Duration.ofDays(20), Duration.ofDays(1)); // active in 30 days
        UUID acc = account(u, false);
        ZonedDateTime monday = weekStart.minusWeeks(2);
        completedImport(u, acc, monday.plusDays(1).toOffsetDateTime());
        completedImport(u, acc, monday.plusDays(2).toOffsetDateTime()); // same week: 1 check-in
        completedImport(u, acc, monday.minusMinutes(30).toOffsetDateTime()); // Sunday 23:30 in São Paulo: week before
        completedImport(u, acc, weekStart.plusMinutes(1).toOffsetDateTime()); // current week: not complete
        jdbc.update("INSERT INTO transaction_imports (user_id, account_id, file_name, file_type, file_size_kb, status, "
            + "updated_at) VALUES (?, ?, 'x.ofx', 'OFX', 1, 'FAILED', ?)", u, acc, monday.plusDays(8).toOffsetDateTime());

        // Imported in the 4 weeks but no session in 30 days: out, like in the denominator
        UUID gone = user(false, false);
        session(gone, Duration.ofDays(60), Duration.ofDays(31));
        completedImport(gone, account(gone, false), monday.plusDays(1).toOffsetDateTime());

        assertThat(summary().getCheckins() - before.getCheckins()).isEqualTo(2);
    }

    @Test
    void deniedTrialIsNotAnEndedTrial() {
        Summary before = summary();
        MonthCounts monthBefore = currentMonth();
        // Returning e-mail without a right to a trial (SubscriptionService.startTrial): start = end = now
        OffsetDateTime at = now.minusMinutes(1);
        subscription(user(false, false), "TRIAL", "EXPIRED", at, at);

        assertThat(summary().getEndedTrials()).isEqualTo(before.getEndedTrials());
        assertThat(currentMonth().getEligible()).isEqualTo(monthBefore.getEligible());
        assertThat(currentMonth().getUsers() - monthBefore.getUsers()).isEqualTo(1);
    }

    @Test
    void conversionByMonthCountsTheUsersCreatedThisMonth() {
        MonthCounts before = currentMonth();
        subscription(user(false, false), "MONTHLY", "CANCELLED", null);
        subscription(user(false, false), "TRIAL", "EXPIRED", now.minusDays(1)); // ended trial
        subscription(user(false, false), "TRIAL", "ACTIVE", now.plusDays(10)); // running: out of the denominator
        user(false, false); // no subscription

        MonthCounts after = currentMonth();

        assertThat(after.getUsers() - before.getUsers()).isEqualTo(4);
        assertThat(after.getPaid() - before.getPaid()).isEqualTo(1);
        assertThat(after.getEligible() - before.getEligible()).isEqualTo(2);
    }

    @Test
    void onboardingCountsOnlyTheCompleted() {
        Summary before = summary();
        onboarding(user(false, false), true);
        onboarding(user(false, false), false);

        Summary after = summary();

        assertThat(after.getOnboarded() - before.getOnboarded()).isEqualTo(1);
        assertThat(after.getTotal() - before.getTotal()).isEqualTo(2);
    }

    @Test
    void adoptionIgnoresDeletedRows() {
        Adoption before = repository.adoption();
        UUID active = user(false, false);
        UUID acc = account(active, false);
        UUID other = account(active, false);
        UUID category = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, 'Mercado', NULL, '#000000', 'tag', FALSE)", category, active);
        jdbc.update("INSERT INTO budget_allocations (user_id, category_id, month_year, allocated_amount) "
            + "VALUES (?, ?, DATE '2026-11-01', 100)", active, category);
        jdbc.update("INSERT INTO reconciliation_history (account_id, user_id, previous_balance, bank_balance, "
            + "difference, reconciled_at) VALUES (?, ?, 0, 0, 0, NOW())", acc, active);
        softDeletable(active, acc, other, false);

        UUID deleted = user(false, false); // only deleted rows
        softDeletable(deleted, account(deleted, true), account(deleted, true), true);

        Adoption after = repository.adoption();

        assertThat(after.getAccounts() - before.getAccounts()).isEqualTo(1);
        assertThat(after.getCreditCards() - before.getCreditCards()).isEqualTo(1);
        assertThat(after.getBudgets() - before.getBudgets()).isEqualTo(1);
        assertThat(after.getGoals() - before.getGoals()).isEqualTo(1);
        assertThat(after.getTransfers() - before.getTransfers()).isEqualTo(1);
        assertThat(after.getReconciliations() - before.getReconciliations()).isEqualTo(1);
        assertThat(after.getImports()).isEqualTo(before.getImports());

        UUID importer = user(false, false);
        completedImport(importer, account(importer, false), now);
        UUID failed = user(false, false);
        jdbc.update("INSERT INTO transaction_imports (user_id, account_id, file_name, file_type, file_size_kb, status) "
            + "VALUES (?, ?, 'x.ofx', 'OFX', 1, 'FAILED')", failed, account(failed, false));
        assertThat(repository.adoption().getImports() - before.getImports()).isEqualTo(1);
    }

    /** A credit card, a goal and a transfer of {@code user}, deleted or not. */
    private void softDeletable(UUID user, UUID from, UUID to, boolean deleted) {
        String deletedAt = deleted ? "NOW()" : "NULL";
        jdbc.update("INSERT INTO credit_cards (user_id, card_name, card_limit, due_day, deleted_at) "
            + "VALUES (?, 'Cartão', 1000, 10, " + deletedAt + ")", user);
        jdbc.update("INSERT INTO goals (user_id, goal_name, target_amount, deleted_at) "
            + "VALUES (?, 'Viagem', 1000, " + deletedAt + ")", user);
        jdbc.update("INSERT INTO transfers (user_id, from_account_id, to_account_id, amount, transfer_date, deleted_at) "
            + "VALUES (?, ?, ?, 10, CURRENT_DATE, " + deletedAt + ")", user, from, to);
    }

    @Test
    void routeAndCsvCarryOnlyAggregates() throws Exception {
        UUID u = user(false, false);
        session(u, Duration.ofHours(2), Duration.ofHours(1));
        subscription(u, "MONTHLY", "ACTIVE", null);

        String body = mockMvc.perform(get("/api/v1/admin/users").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode m = json.readTree(body);
        assertThat(m.get("months")).hasSize(12);
        assertThat(m.get("growth")).hasSize(12);
        assertThat(m.get("conversionByMonth")).hasSize(12);
        assertThat(m.get("weeks")).hasSize(12);
        assertThat(m.get("activeWeekly")).hasSize(12);
        assertThat(m.get("activeMonthly")).hasSize(12);
        assertThat(m.get("adoption")).hasSize(7);
        assertThat(m.get("growth").get(11).asLong()).isEqualTo(m.get("total").asLong());
        assertThat(m.get("activeWeekly").get(11).asLong()).isEqualTo(m.get("active7d").asLong());
        assertThat(m.get("activeMonthly").get(11).asLong()).isEqualTo(m.get("active30d").asLong());
        assertThat(m.get("churn").get("target").asDouble()).isEqualTo(5.0);
        assertThat(m.get("onboarding").get("target").asDouble()).isEqualTo(70.0);
        assertThat(body).doesNotContain(u.toString()).doesNotContain(admin.toString()).doesNotContain("@example.com");

        String today = LocalDate.now(SAO_PAULO).toString();
        String csv = mockMvc.perform(get("/api/v1/admin/users/export").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"usuarios_" + today + ".csv\""))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Métrica;Valor\r\nTotal de usuários;");
        assertThat(csv.split("\r\n")).hasSize(18);
        assertThat(csv).doesNotContain(u.toString()).doesNotContain("@example.com");
    }

    @Test
    void nonAdminsGet403() throws Exception {
        UUID plain = user(false, false);
        mockMvc.perform(get("/api/v1/admin/users").with(jwt().jwt(j -> j.subject(plain.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso restrito"));
        mockMvc.perform(get("/api/v1/admin/users/export").with(jwt().jwt(j -> j.subject(plain.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso restrito"));
    }
}
