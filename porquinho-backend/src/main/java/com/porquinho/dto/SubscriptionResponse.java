package com.porquinho.dto;

import com.porquinho.entity.Subscription;

import java.time.OffsetDateTime;

/**
 * Subscription status returned by GET /api/v1/subscriptions/me (Story 2.1;
 * nextBillingDate/cancellationDate added in Story 2.4; failedPaymentAttempts/paymentUpdateUrl in Story 2.7:
 * paymentUpdateUrl is the Stripe hosted page of the failed invoice, set only while PAYMENT_FAILED).
 */
public record SubscriptionResponse(
    String planType,
    String status,
    OffsetDateTime trialStartDate,
    OffsetDateTime trialEndDate,
    OffsetDateTime nextBillingDate,
    OffsetDateTime cancellationDate,
    int failedPaymentAttempts,
    String paymentUpdateUrl,
    long daysRemaining,
    boolean hasAccess
) {

    public static SubscriptionResponse of(Subscription subscription, long daysRemaining, boolean hasAccess) {
        return new SubscriptionResponse(
            subscription.getPlanType().name(),
            subscription.getStatus().name(),
            subscription.getTrialStartDate(),
            subscription.getTrialEndDate(),
            subscription.getNextBillingDate(),
            subscription.getCancellationDate(),
            subscription.getFailedPaymentAttempts(),
            subscription.getPaymentUpdateUrl(),
            daysRemaining,
            hasAccess
        );
    }
}
