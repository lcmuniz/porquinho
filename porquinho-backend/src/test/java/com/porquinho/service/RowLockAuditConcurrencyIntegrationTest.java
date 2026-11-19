package com.porquinho.service;

import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Stories 2.2 and 3.1: the row lock of findByUserIdForUpdate (subscription and onboarding_progress) makes two
 * concurrent requests write one audit, against the real PostgreSQL. The audit call of each thread waits for the other
 * one (up to 2 s) before writing: without the lock both threads read the old state and meet there, so both audit;
 * with the lock the second one is still blocked on the SELECT ... FOR UPDATE, the first gives up waiting, commits,
 * and the second sees the new state. Not @Transactional: each call commits on its own thread. Cleans up after.
 */
@SpringBootTest
@ActiveProfiles("test")
// The spy gives this class its own context (and Hikari pool); closing it keeps the suite under max_connections
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RowLockAuditConcurrencyIntegrationTest {

    @Autowired
    private SubscriptionWebhookService subscriptionWebhookService;
    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditLogService auditLogService;
    @MockitoBean
    private EmailService emailService;

    private final CyclicBarrier bothAtTheAudit = new CyclicBarrier(2);
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM onboarding_progress WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    /** The audit {@code event} of this user waits for the other thread to get there too, then is written. */
    private void auditWaitsForTheOtherThread(String event) {
        doAnswer(inv -> {
            try {
                bothAtTheAudit.await(2, TimeUnit.SECONDS);
            } catch (TimeoutException | BrokenBarrierException e) {
                // the other thread never got here: the lock held it back
            }
            return inv.callRealMethod();
        }).when(auditLogService).log(eq(event), eq(userId), any(), any());
    }

    private static void runTwiceAtOnce(Callable<?> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> results = List.of(1, 2).stream().<Future<?>>map(i -> pool.submit(() -> {
                start.await();
                return call.call();
            })).toList();
            start.countDown();
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private int audits(String event) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = ?",
            Integer.class, userId, event);
    }

    @Test
    void sameCheckoutDeliveredTwiceAtOnceActivatesAndAuditsOnce() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        subscriptionRepository.save(new Subscription(userId, PlanType.TRIAL, Status.ACTIVE, now.minusDays(20),
            now.plusDays(10)));
        auditWaitsForTheOtherThread("subscription_activated");
        OffsetDateTime nextBilling = now.plusDays(40);

        runTwiceAtOnce(() -> subscriptionWebhookService.activateMonthly(userId, "cus_1", "sub_1", "pm_1", nextBilling));

        assertThat(audits("subscription_activated")).isEqualTo(1);
        verify(emailService, times(1)).sendSubscriptionActivated(anyString(), any());
        assertThat(jdbc.queryForObject("SELECT stripe_subscription_id FROM subscriptions WHERE user_id = ?",
            String.class, userId)).isEqualTo("sub_1");
    }

    @Test
    void tutorialCompletedTwiceAtOnceAuditsOnce() throws Exception {
        onboardingService.getOrCreate(userId);
        auditWaitsForTheOtherThread(OnboardingService.AUDIT_TUTORIAL_COMPLETED);

        runTwiceAtOnce(() -> onboardingService.completeTutorial(userId, false, "127.0.0.1"));

        assertThat(audits(OnboardingService.AUDIT_TUTORIAL_COMPLETED)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_step FROM onboarding_progress WHERE user_id = ?", Integer.class,
            userId)).isEqualTo(2);
    }
}
