package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.repository.AuditLogRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Story 2.3 through the real Spring proxies, the real candidate query and PostgreSQL. The mocked
 * e-mail checks, when called, that it runs after commit (sent_at already committed in the DB).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class TrialReminderServiceIntegrationTest {

    @Autowired
    private TrialReminderService trialReminderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private DataSource dataSource;

    @MockBean
    private EmailService emailService;

    /** Problems seen inside the e-mail call; asserted after the job (exceptions there would be swallowed). */
    private final List<String> afterCommitViolations = new CopyOnWriteArrayList<>();

    @BeforeEach
    void checkEmailRunsAfterCommit() {
        doAnswer(inv -> {
            String email = inv.getArgument(0);
            long daysLeft = inv.getArgument(1);
            // Own connection (not the one bound to the job's transaction): sees only COMMITTED data, so an
            // e-mail sent before commit finds sent_at NULL. isActualTransactionActive() cannot tell: Spring
            // keeps it true while afterCommit callbacks run.
            String column = daysLeft <= 1 ? "trial_reminder_1d_sent_at" : "trial_reminder_7d_sent_at";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT s." + column + " FROM subscriptions s JOIN users u ON u.id = s.user_id WHERE u.email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        afterCommitViolations.add(email + ": " + column + " not committed before e-mail");
                    }
                }
            }
            return null;
        }).when(emailService).sendTrialReminder(anyString(), anyLong(), any(), anyString());
    }

    private User userWithSubscription(PlanType plan, OffsetDateTime end, OffsetDateTime sevenDaySentAt) {
        String email = "trial-reminder-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(email, AuthProvider.EMAIL, null));
        Subscription s = new Subscription(user.getId(), plan, Status.ACTIVE, end.minusDays(30), end);
        s.setTrialReminder7dSentAt(sevenDaySentAt);
        subscriptionRepository.save(s);
        return user;
    }

    /** Same time of day, N days ahead: always N Sao Paulo calendar days. */
    private static OffsetDateTime inDays(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void sevenDayReminderIsSentOnceAcrossRuns() {
        User user = userWithSubscription(PlanType.TRIAL, inDays(7), null);

        trialReminderService.sendTrialReminders();
        trialReminderService.sendTrialReminders();

        verify(emailService, times(1)).sendTrialReminder(eq(user.getEmail()), eq(7L), any(),
            argThat(url -> url.endsWith("/assinatura")));
        Subscription saved = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(saved.getTrialReminder7dSentAt()).isNotNull();
        assertThat(saved.getTrialReminder1dSentAt()).isNull();
        assertThat(auditLogRepository.findAll())
            .filteredOn(a -> user.getId().equals(a.getUserId()) && "trial_reminder_sent".equals(a.getEventType()))
            .hasSize(1);
        assertThat(afterCommitViolations).isEmpty();
    }

    @Test
    void oneDayReminderAfterSevenDaySentGoesThroughRealQuery() {
        User user = userWithSubscription(PlanType.TRIAL, inDays(1), OffsetDateTime.now(ZoneOffset.UTC).minusDays(6));

        trialReminderService.sendTrialReminders();

        verify(emailService, times(1)).sendTrialReminder(eq(user.getEmail()), anyLong(), any(), anyString());
        verify(emailService).sendTrialReminder(eq(user.getEmail()), eq(1L), any(), anyString());
        assertThat(subscriptionRepository.findByUserId(user.getId()).orElseThrow().getTrialReminder1dSentAt())
            .isNotNull();
        assertThat(afterCommitViolations).isEmpty();
    }

    @Test
    void monthlyPlanIsNotProcessed() {
        User user = userWithSubscription(PlanType.MONTHLY, inDays(9), null);

        trialReminderService.sendTrialReminders();

        verify(emailService, never()).sendTrialReminder(eq(user.getEmail()), anyLong(), any(), anyString());
        Subscription saved = subscriptionRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(saved.getTrialReminder7dSentAt()).isNull();
        assertThat(saved.getTrialReminder1dSentAt()).isNull();
    }
}
