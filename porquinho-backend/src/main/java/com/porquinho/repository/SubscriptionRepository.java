package com.porquinho.repository;

import com.porquinho.entity.Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Subscription entity (Story 2.1).
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * Row lock for webhook processing (Story 2.2): Stripe may deliver the same event twice
     * concurrently; the lock serializes them so the idempotency check sees the first write.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId")
    Optional<Subscription> findByUserIdForUpdate(@Param("userId") UUID userId);

    /**
     * Hard-delete the user's subscription (Story 1.7 account purge, extended in Story 2.1).
     * Must run BEFORE deleting the user row (FK subscriptions.user_id -> users.id).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM subscriptions WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
