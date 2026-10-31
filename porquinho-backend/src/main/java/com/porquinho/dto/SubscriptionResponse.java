package com.porquinho.dto;

import com.porquinho.entity.Subscription;

import java.time.OffsetDateTime;

/**
 * Subscription status returned by GET /api/v1/subscriptions/me (Story 2.1).
 */
public record SubscriptionResponse(
    String planType,
    String status,
    OffsetDateTime trialStartDate,
    OffsetDateTime trialEndDate,
    long daysRemaining,
    boolean hasAccess
) {

    public static SubscriptionResponse of(Subscription subscription, long daysRemaining, boolean hasAccess) {
        return new SubscriptionResponse(
            subscription.getPlanType().name(),
            subscription.getStatus().name(),
            subscription.getTrialStartDate(),
            subscription.getTrialEndDate(),
            daysRemaining,
            hasAccess
        );
    }
}
