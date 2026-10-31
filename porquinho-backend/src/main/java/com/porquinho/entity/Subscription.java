package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * User subscription (Story 2.1; Stripe ids added in Story 2.2). One row per user for its whole lifetime.
 * No @Where on purpose: the LGPD purge hard-deletes this row (see Story 1.7 learnings).
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    public enum PlanType { TRIAL, MONTHLY }

    public enum Status { ACTIVE, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "trial_start_date")
    private OffsetDateTime trialStartDate;

    @Column(name = "trial_end_date")
    private OffsetDateTime trialEndDate;

    @Column(name = "next_billing_date")
    private OffsetDateTime nextBillingDate;

    @Column(name = "payment_method_id")
    private String paymentMethodId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Subscription() {
    }

    public Subscription(UUID userId, PlanType planType, Status status,
                        OffsetDateTime trialStartDate, OffsetDateTime trialEndDate) {
        this.userId = userId;
        this.planType = planType;
        this.status = status;
        this.trialStartDate = trialStartDate;
        this.trialEndDate = trialEndDate;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType planType) { this.planType = planType; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public OffsetDateTime getTrialStartDate() { return trialStartDate; }
    public void setTrialStartDate(OffsetDateTime trialStartDate) { this.trialStartDate = trialStartDate; }

    public OffsetDateTime getTrialEndDate() { return trialEndDate; }
    public void setTrialEndDate(OffsetDateTime trialEndDate) { this.trialEndDate = trialEndDate; }

    public OffsetDateTime getNextBillingDate() { return nextBillingDate; }
    public void setNextBillingDate(OffsetDateTime nextBillingDate) { this.nextBillingDate = nextBillingDate; }

    public String getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
