package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
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
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Retro Epic 2 F1 through the real Spring transactions: the account deletion (executeDeletionTransaction joined by
 * cancelForAccountDeletion) cancels the Stripe subscription only after the deletion committed, and never when it
 * rolls back. Unit tests fake the synchronization by hand; here a propagation change (REQUIRES_NEW, or no join)
 * would call Stripe before the outer commit. Same mocks as RetentionServiceIntegrationTest, so the context is shared.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AccountDeletionAfterCommitIntegrationTest {

    @Autowired
    private AccountDeletionService accountDeletionService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EmailService emailService;
    @MockBean
    private SupabaseAdminClient supabaseAdminClient;
    @MockBean
    private StripeBillingService stripeBillingService;

    /** Subscription status (and user soft delete) committed when Stripe was called, seen from another connection. */
    private final List<String> seenByStripe = new CopyOnWriteArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Subscription s = new Subscription(userId, PlanType.MONTHLY, Status.ACTIVE, now.minusDays(60), now.minusDays(30));
        s.setStripeSubscriptionId("sub_" + userId);
        s.setNextBillingDate(now.plusDays(10));
        subscriptionRepository.save(s);

        doAnswer(inv -> {
            // Own connection, not the one bound to the deletion: sees only committed data
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT s.status, u.deleted_at IS NOT NULL FROM subscriptions s "
                     + "JOIN users u ON u.id = s.user_id WHERE s.user_id = ?")) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    seenByStripe.add(rs.getString(1) + ", user deleted: " + rs.getBoolean(2));
                }
            }
            return "in_1";
        }).when(stripeBillingService).cancelImmediately(any());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_deletions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void stripeIsCancelledOnlyAfterTheDeletionCommits() {
        accountDeletionService.executeDeletionTransaction(userId, "127.0.0.1");

        verify(stripeBillingService).cancelImmediately("sub_" + userId);
        assertThat(seenByStripe).containsExactly("EXPIRED, user deleted: true");
    }

    @Test
    void stripeIsNeverCalledWhenTheDeletionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            accountDeletionService.executeDeletionTransaction(userId, "127.0.0.1");
            status.setRollbackOnly();
        });

        verify(stripeBillingService, never()).cancelImmediately(any());
        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE user_id = ?", String.class, userId))
            .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM users WHERE id = ?", Boolean.class, userId))
            .isTrue();
    }
}
