package com.porquinho.repository;

import com.porquinho.entity.BillingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for BillingTransaction (Story 2.6).
 */
@Repository
public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, UUID> {

    /** Webhook idempotency: stripe_transaction_id is the Stripe invoice id (UNIQUE in V13). */
    boolean existsByStripeTransactionId(String stripeTransactionId);

    /** Data export (LGPD portability): newest payment first. */
    List<BillingTransaction> findByUserIdOrderByPaidAtDesc(UUID userId);

    /**
     * Hard-delete the user's payments (Story 1.7 account purge, extended in Story 2.6).
     * Must run BEFORE deleting subscriptions and users (FKs billing_transactions -> subscriptions/users).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM billing_transactions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
