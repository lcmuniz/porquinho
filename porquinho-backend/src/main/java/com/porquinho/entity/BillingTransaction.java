package com.porquinho.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A monthly-plan payment confirmed by Stripe (Story 2.6, invoice.paid webhook). Stripe charges;
 * this row only records it. stripe_transaction_id is the invoice id (in_...), UNIQUE: the
 * idempotency key of the webhook. Hard-deleted by the LGPD purge (before subscriptions).
 */
@Entity
@Table(name = "billing_transactions")
public class BillingTransaction {

    /** Only PAID for now; Story 2.7 adds FAILED. */
    public enum Status { PAID }

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "stripe_transaction_id", nullable = false, unique = true)
    private String stripeTransactionId;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public BillingTransaction() {
    }

    public BillingTransaction(UUID subscriptionId, UUID userId, BigDecimal amount, String currency, Status status,
                              String paymentMethod, String stripeTransactionId, OffsetDateTime paidAt) {
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.stripeTransactionId = stripeTransactionId;
        this.paidAt = paidAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getStripeTransactionId() { return stripeTransactionId; }
    public void setStripeTransactionId(String stripeTransactionId) { this.stripeTransactionId = stripeTransactionId; }

    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
