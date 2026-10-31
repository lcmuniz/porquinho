package com.porquinho.repository;

import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 2.6: billing_transactions against the real schema (PostgreSQL via Testcontainers, Flyway V13):
 * entity mapping, the native purge delete in the purge's FK order, the export ordering and the
 * invoice-id UNIQUE that backs webhook idempotency.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillingTransactionRepositoryIntegrationTest {

    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2026-11-30T12:00:00Z");

    @Autowired
    private BillingTransactionRepository billingTransactionRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        userId = insertUser();
        subscription = saveSubscription(userId, "sub_" + userId);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        return id;
    }

    private Subscription saveSubscription(UUID owner, String stripeSubscriptionId) {
        Subscription s = new Subscription(owner, PlanType.MONTHLY, Status.ACTIVE, PAID_AT.minusDays(60), PAID_AT.minusDays(30));
        s.setStripeSubscriptionId(stripeSubscriptionId);
        s.setNextBillingDate(PAID_AT);
        return subscriptionRepository.saveAndFlush(s);
    }

    private BillingTransaction pay(Subscription sub, String invoiceId, OffsetDateTime paidAt) {
        return billingTransactionRepository.saveAndFlush(new BillingTransaction(sub.getId(), sub.getUserId(),
            new BigDecimal("19.90"), "brl", BillingTransaction.Status.PAID, "pm_1", invoiceId, paidAt));
    }

    private int countBillingRows(UUID owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_transactions WHERE user_id = ?", Integer.class, owner);
    }

    @Test
    void purgeOrderDeletesOnlyThatUsersPaymentsThenSubscriptionThenUser() {
        pay(subscription, "in_a1", PAID_AT.minusMonths(1));
        pay(subscription, "in_a2", PAID_AT);
        UUID otherUser = insertUser();
        pay(saveSubscription(otherUser, "sub_" + otherUser), "in_b1", PAID_AT);

        assertThat(billingTransactionRepository.existsByStripeTransactionId("in_a1")).isTrue();

        // Same order as AccountDeletionService.purgeUser
        assertThat(billingTransactionRepository.deleteByUserId(userId)).isEqualTo(2);
        assertThat(subscriptionRepository.deleteByUserId(userId)).isEqualTo(1);
        assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);

        assertThat(countBillingRows(userId)).isZero();
        assertThat(billingTransactionRepository.existsByStripeTransactionId("in_a1")).isFalse();
        assertThat(countBillingRows(otherUser)).isEqualTo(1);
    }

    @Test
    void subscriptionCannotBeDeletedWhilePaymentsReferenceIt() {
        // Why the purge deletes billing_transactions first
        pay(subscription, "in_fk", PAID_AT);

        assertThatThrownBy(() -> subscriptionRepository.deleteByUserId(userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exportListsPaymentsNewestFirstWithAmountAsDecimal() {
        pay(subscription, "in_old", PAID_AT.minusMonths(2));
        pay(subscription, "in_new", PAID_AT);
        pay(subscription, "in_mid", PAID_AT.minusMonths(1));

        List<BillingTransaction> found = billingTransactionRepository.findByUserIdOrderByPaidAtDesc(userId);

        assertThat(found).extracting(BillingTransaction::getStripeTransactionId)
            .containsExactly("in_new", "in_mid", "in_old");
        assertThat(found.get(0).getAmount()).isEqualByComparingTo("19.90");
        assertThat(found.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void sameInvoiceCannotBeStoredTwice() {
        pay(subscription, "in_dup", PAID_AT);

        assertThatThrownBy(() -> pay(subscription, "in_dup", PAID_AT))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
