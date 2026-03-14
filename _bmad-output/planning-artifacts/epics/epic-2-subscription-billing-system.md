# Epic 2: Subscription & Billing System

Usuários recebem trial gratuito de 30 dias automaticamente, podem assinar plano mensal após trial, gerenciar assinatura (cancelar/reativar) de forma self-service, e recebem lembretes e notificações sobre status de pagamento.

## Story 2.1: Automatically Activate 30-Day Free Trial

As a new user,
I want to automatically receive a 30-day free trial when I register,
So that I can explore all features without payment commitment.

**Acceptance Criteria:**

**Given** I complete user registration (Epic 1, Story 1.1 or 1.2)
**When** My account is created
**Then** A `subscriptions` table is created via Flyway migration (V5__create_subscriptions_table.sql) with fields: subscription_id (UUID, PK), user_id (FK), plan_type (trial/monthly), status (active/cancelled/expired), trial_start_date, trial_end_date, next_billing_date, payment_method_id, created_at, updated_at, deleted_at
**And** A subscription record is automatically created with plan_type='trial', status='active'
**And** trial_start_date is set to current timestamp
**And** trial_end_date is set to 30 days from now
**And** I have full access to all features during trial period
**And** Subscription status is checked on every authenticated request
**And** If current_date < trial_end_date, access is granted
**And** Dashboard shows "Trial: X days remaining" badge

---

## Story 2.2: Subscribe to Monthly Plan After Trial

As a trial user,
I want to subscribe to the monthly plan,
So that I can continue using the service after my trial expires.

**Acceptance Criteria:**

**Given** I am on trial and on the subscription page
**When** I click "Subscribe to Monthly Plan"
**Then** I am redirected to payment gateway (Stripe) checkout page
**And** I enter payment details (credit card or other supported method)
**And** After successful payment, webhook from Stripe is received (NFR59)
**And** Subscription record is updated: plan_type='monthly', status='active', next_billing_date=30 days from now
**And** payment_method_id is saved (tokenized payment method from Stripe)
**And** Email confirmation is sent: "Subscription Activated - Monthly Plan"
**And** Dashboard badge changes from "Trial" to "Active Subscription"
**And** If payment fails, error message is shown with retry option
**And** If payment fails, trial continues until resolved or expires
**And** Audit log entry is created for "subscription_activated" event

---

## Story 2.3: Send Trial Expiration Reminders

As a trial user,
I want to receive reminders before my trial expires,
So that I have time to subscribe and avoid losing access.

**Acceptance Criteria:**

**Given** I am on an active trial
**When** My trial is 7 days from expiration
**Then** Email reminder is sent: "Your trial expires in 7 days"
**And** Email includes CTA button: "Subscribe Now"
**And** When my trial is 1 day from expiration
**Then** Second email reminder is sent: "Your trial expires tomorrow"
**And** Email includes urgent CTA and benefits of subscribing
**And** Background job (cron or scheduled task) runs daily to check trial expiration dates
**And** Reminders are sent at 9 AM user's local time (or UTC if timezone not available)
**And** Audit log records "trial_reminder_sent" events
**And** If user subscribes between reminders, no further reminders are sent

---

## Story 2.4: Self-Service Subscription Cancellation

As a subscribed user,
I want to cancel my subscription at any time,
So that I have control over my recurring payments.

**Acceptance Criteria:**

**Given** I am logged in with an active monthly subscription
**When** I navigate to subscription settings and click "Cancel Subscription"
**Then** Confirmation modal is shown: "Are you sure? You'll lose access at the end of current billing period"
**And** I confirm cancellation
**And** Subscription record is updated: status='cancelled', cancellation_date=NOW()
**And** Access continues until end of current billing period (next_billing_date)
**And** next_billing_date is not updated (no future charges)
**And** Payment method is not charged again
**And** Email confirmation is sent: "Subscription Cancelled"
**And** Dashboard shows "Subscription: Cancelled (Active until [next_billing_date])"
**And** Audit log entry is created for "subscription_cancelled" event
**And** After next_billing_date, status changes to 'expired' and access is revoked
**And** Cancellation request is sent to payment gateway (Stripe) to stop recurring billing

---

## Story 2.5: Reactivate Cancelled Subscription

As a user with cancelled subscription,
I want to reactivate my subscription,
So that I can resume using the service.

**Acceptance Criteria:**

**Given** I have a cancelled subscription (status='cancelled' or 'expired')
**When** I navigate to subscription page and click "Reactivate Subscription"
**Then** If payment method is still valid, reactivation happens immediately
**And** If payment method is invalid/expired, I am prompted to update payment details
**And** After successful reactivation, subscription record is updated: status='active', next_billing_date=30 days from now
**And** Immediate payment is processed for first month
**And** Email confirmation is sent: "Subscription Reactivated"
**And** Dashboard shows "Active Subscription" badge
**And** Audit log entry is created for "subscription_reactivated" event
**And** If payment fails, error message is shown with retry option

---

## Story 2.6: Process Recurring Monthly Payments

As a system administrator,
I want the system to automatically process recurring monthly payments,
So that subscriptions remain active without manual intervention.

**Acceptance Criteria:**

**Given** A user has an active monthly subscription
**When** next_billing_date is reached
**Then** Background job (cron) runs daily to check subscriptions due for billing
**And** Payment request is sent to payment gateway (Stripe) using saved payment_method_id
**And** If payment succeeds (NFR58: 99.9% reliability):
  - Subscription record is updated: next_billing_date += 30 days
  - Email receipt is sent to user
  - Audit log entry is created for "payment_success" event
**And** If payment fails:
  - Story 2.7 retry logic is triggered
**And** Payment processing happens at 2 AM UTC to minimize user impact
**And** Webhook from payment gateway confirms transaction status (NFR59)
**And** Transaction record is created in `billing_transactions` table (new migration V6__create_billing_transactions_table.sql) with fields: transaction_id, subscription_id, amount, currency, status, payment_method, stripe_transaction_id, created_at

---

## Story 2.7: Handle Payment Failures with Retry & Notifications

As a subscribed user,
I want to be notified if my payment fails and have the system retry automatically,
So that I don't lose access due to temporary payment issues.

**Acceptance Criteria:**

**Given** A recurring payment fails (expired card, insufficient funds, etc.)
**When** Payment failure webhook is received from Stripe
**Then** Subscription record is updated: status='payment_failed', failed_payment_attempts=1
**And** Email notification is sent immediately: "Payment Failed - Action Required"
**And** Email includes reasons (if provided by gateway) and link to update payment method
**And** System automatically retries payment after 3 days (NFR59: retry automático)
**And** If retry succeeds:
  - Subscription status returns to 'active'
  - next_billing_date is updated
  - Email sent: "Payment Successful"
**And** If retry fails, second retry occurs after 7 days (total 2 retries)
**And** If all retries fail (3 attempts over 10 days):
  - Subscription status changes to 'suspended'
  - Access is revoked
  - Final email sent: "Subscription Suspended - Update Payment Method"
**And** User can manually retry payment or update payment method at any time
**And** Audit log records all payment failure and retry events
**And** Dashboard shows "Payment Failed" banner with CTA to update payment method

---
