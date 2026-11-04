# Epic 2: Subscription & Billing System

Usuários recebem trial gratuito de 30 dias automaticamente, podem assinar plano mensal após trial, gerenciar assinatura (cancelar/reativar) de forma self-service, e recebem lembretes e notificações sobre status de pagamento.

> **Como construído (retro do Epic 2, 2026-11-01, item 5):** os critérios abaixo foram atualizados para refletir o que foi implementado. Specs com as decisões: `_bmad-output/implementation-artifacts/2-*.md`, `spec-retro-epic2-f1-stop-billing-on-account-deletion.md` e `spec-retro-epic2-f2-refund-duplicate-checkout.md`. A maior mudança: o plano previa cobrança própria (cron às 2h UTC, retry próprio, `next_billing_date += 30 dias`); o construído deixa cobrança, novas tentativas e cancelamento final com o **Stripe Billing**, e o porquinho reage aos webhooks `checkout.session.completed`, `invoice.paid`, `invoice.payment_failed` e `customer.subscription.deleted`. Plano único de **R$ 19,90/mês**, só cartão (Stripe Checkout hospedado). Status: `ACTIVE`, `CANCELLED`, `EXPIRED`, `PAYMENT_FAILED`, `SUSPENDED`. As tabelas usam `id` como chave primária e as migrations são V9 a V14 (V5 a V8 já existiam). NFR59 é o retry de entrega de webhook, não de cobrança.

## Story 2.1: Automatically Activate 30-Day Free Trial

As a new user,
I want to automatically receive a 30-day free trial when I register,
So that I can explore all features without payment commitment.

**Acceptance Criteria:**

**Given** I complete user registration (Epic 1, Story 1.1 or 1.2)
**When** My account is created
**Then** A `subscriptions` table is created via Flyway migration (V9__create_subscriptions_table.sql) with fields: id (UUID, PK), user_id (FK, UNIQUE), plan_type (TRIAL/MONTHLY), status (ACTIVE/CANCELLED/EXPIRED; V14 adds PAYMENT_FAILED/SUSPENDED), trial_start_date, trial_end_date, next_billing_date, payment_method_id, created_at, updated_at, deleted_at
**And** Existing users without a subscription receive a 30-day trial from the migration date (backfill)
**And** A subscription record is created in the same transaction as the user, with plan_type='TRIAL', status='ACTIVE', and audit "trial_started"
**And** trial_start_date is set to current timestamp (UTC)
**And** trial_end_date is set to 30 days from now
**And** Repeated (idempotent) registration calls do not create or restart the trial
**And** I have full access to all features during trial period
**And** Subscription status is checked on every authenticated request to `/api/v1/**`; without access the API returns **402 Payment Required** (ProblemDetail) and the frontend redirects to `/assinatura`
**And** Auth, subscription and LGPD rights routes (export, deletion) always pass, even without access
**And** If current_date < trial_end_date, access is granted
**And** Dashboard shows "Trial: X dias restantes" badge ("1 dia restante" singular; "Trial expirado" after the end); if the API fails, the badge is hidden and the dashboard still works
**And** Data export (Story 1.6) includes the subscription, and the purge (Story 1.7) deletes it before the user

---

## Story 2.2: Subscribe to Monthly Plan After Trial

As a trial user,
I want to subscribe to the monthly plan,
So that I can continue using the service after my trial expires.

**Acceptance Criteria:**

**Given** I am on trial (active or expired) and on the subscription page (`/assinatura`, linked from the dashboard)
**When** I click "Assinar" on the single plan (R$ 19,90/mês)
**Then** I am redirected to the hosted Stripe Checkout page (mode subscription, card only, price inline: BRL 1990, monthly)
**And** I enter my card in Stripe (the porquinho never sees or stores card data; no Pix/boleto)
**And** Subscribing during the trial keeps the free days: the first charge happens on trial_end_date (decision A, Luiz, 2026-10-31); with the trial already expired, it charges immediately
**And** State changes only through the `checkout.session.completed` webhook with a verified signature, never through the redirect (NFR59: the webhook is idempotent and Stripe redelivers on error)
**And** Subscription record is updated: plan_type='MONTHLY', status='ACTIVE', next_billing_date = end of the current Stripe period
**And** stripe_customer_id, stripe_subscription_id (V10__add_stripe_ids_to_subscriptions.sql) and payment_method_id are saved
**And** Email confirmation is sent: "Assinatura ativada"
**And** Dashboard badge changes from "Trial" to "Assinatura ativa"
**And** Checkout when already MONTHLY returns 409 with the reason in the detail (active, cancelled with access, pending invoice), shown on screen
**And** If Stripe is unavailable, 503 and the trial is unchanged; if the user gives up on Checkout, they return to `/assinatura` with a notice and the trial intact
**And** A duplicate completed Checkout is cancelled in Stripe and its paid invoice is refunded automatically, never recorded nor receipted (retro F2, decision A, Luiz, 2026-11-01)
**And** Audit log entry is created for "subscription_activated" event

---

## Story 2.3: Send Trial Expiration Reminders

As a trial user,
I want to receive reminders before my trial expires,
So that I have time to subscribe and avoid losing access.

**Acceptance Criteria:**

**Given** I am on an active trial
**When** My trial is 7 days from expiration
**Then** Email reminder is sent: "Seu trial termina em 7 dias"
**And** Email includes CTA link "Assinar agora" to `/assinatura`
**And** When my trial is 1 day from expiration
**Then** Second email reminder is sent: "Seu trial termina amanhã"
**And** Email includes the end date and short benefits of subscribing
**And** Background job runs daily at 9 AM São Paulo time (decision, Luiz, 2026-10-31: Brazilian audience, no per-user timezone); days are counted in São Paulo calendar dates
**And** Each reminder is sent at most once per user (V11__add_trial_reminder_tracking.sql: trial_reminder_7d_sent_at, trial_reminder_1d_sent_at), even if the job runs twice; a missed day still sends the pending reminder, but never both at once
**And** A failed email for one user does not stop the others
**And** Audit log records "trial_reminder_sent" events (metadata 7d/1d)
**And** If user subscribes between reminders, no further reminders are sent

---

## Story 2.4: Self-Service Subscription Cancellation

As a subscribed user,
I want to cancel my subscription at any time,
So that I have control over my recurring payments.

**Acceptance Criteria:**

**Given** I am logged in with an active monthly subscription
**When** I go to `/assinatura` and click "Cancelar assinatura"
**Then** Confirmation modal is shown: "Tem certeza? Você perde o acesso ao fim do período atual (dd/mm/aaaa)"
**And** I confirm cancellation
**And** Cancellation is requested synchronously from Stripe (`cancel_at_period_end=true`); if Stripe fails, 503 and nothing changes locally
**And** Subscription record is updated: status='CANCELLED', cancellation_date=NOW() (V12__add_cancellation_date_to_subscriptions.sql)
**And** Access continues until end of current billing period (next_billing_date), and ends at that date even before the webhook arrives
**And** Payment method is not charged again
**And** Email confirmation is sent: "Assinatura cancelada", with the date access ends
**And** Dashboard shows "Assinatura cancelada (ativa até dd/mm/aaaa)"
**And** Audit log entry is created for "subscription_cancelled" event
**And** When Stripe ends the subscription (`customer.subscription.deleted`), status changes to 'EXPIRED' and audit "subscription_expired" is logged
**And** Cancelling again or cancelling a trial returns 409; an old `checkout.session.completed` never reactivates a CANCELLED/EXPIRED subscription
**And** In PAYMENT_FAILED (Story 2.7, decision 1A of the review) cancellation is immediate: Stripe cancels now, the open invoice is voided, status='EXPIRED', and the email says access ended today

---

## Story 2.5: Reactivate Cancelled Subscription

As a user with cancelled subscription,
I want to reactivate my subscription,
So that I can resume using the service.

**Acceptance Criteria:**

**Given** I have a cancelled subscription (status='CANCELLED'), an expired one (status='EXPIRED') or a suspended one (status='SUSPENDED', Story 2.7)
**When** I go to `/assinatura` and click "Reativar assinatura"
**Then** If it is cancelled and still has access, the cancellation is undone in Stripe (`cancel_at_period_end=false`), with no charge now: the next charge stays on the date already scheduled (decision 1A, Luiz, 2026-10-31)
**And** In that path, the record goes back to status='ACTIVE', cancellation_date=NULL, next_billing_date from Stripe; if Stripe fails, 503 and nothing changes; if Stripe says the subscription already ended, 409 "assine novamente"
**And** If it is expired, suspended, or cancelled without access, the button opens Stripe Checkout, where I confirm or change the card and pay immediately (decision 2A, Luiz, 2026-10-31); the webhook activates it as in Story 2.2
**And** The porquinho never charges the saved card off-session
**And** Email confirmation is sent: "Assinatura reativada", with the next billing date
**And** Dashboard shows "Assinatura ativa" badge (the dashboard CTA for EXPIRED/SUSPENDED reads "Reativar")
**And** Audit log entry is created for "subscription_reactivated" event in both paths (not "subscription_activated")
**And** Reactivating an active subscription, a trial, or a PAYMENT_FAILED subscription returns 409

---

## Story 2.6: Process Recurring Monthly Payments

As a system administrator,
I want the system to record the recurring monthly payments charged by Stripe,
So that subscriptions remain active without manual intervention.

**Acceptance Criteria:**

**Given** A user has an active monthly subscription
**When** Stripe charges the renewal (or the first charge at the end of the trial) and sends `invoice.paid`
**Then** Stripe Billing does the charging; the porquinho has no billing job and never charges off-session (a job of its own would charge twice)
**And** For each paid invoice with amount > 0 of a known subscription:
  - A transaction record is created in `billing_transactions` (V13__create_billing_transactions_table.sql) with fields: id, subscription_id, user_id, amount DECIMAL(15,2), currency, status (PAID), payment_method, stripe_transaction_id (invoice id, UNIQUE), paid_at, created_at
  - next_billing_date moves to the end of the new period reported by Stripe (it only moves forward)
  - Email receipt "Pagamento confirmado" is sent (amount, payment date, next billing)
  - Audit log entry is created for "payment_success" event
**And** Processing is idempotent by invoice id: a redelivered webhook records nothing new (NFR59)
**And** Invoices of R$ 0 (subscription created during the trial) and invoices without a subscription are ignored with 200
**And** If the first invoice (`subscription_create`) arrives before `checkout.session.completed`, the webhook returns 503 so Stripe redelivers (decision A, Luiz, 2026-10-31), unless Stripe reports that subscription as cancelled (duplicate, retro F2), which returns 200
**And** The purge (Story 1.7) deletes `billing_transactions` before `subscriptions`, and the data export includes the table
**And** If payment fails: Story 2.7

---

## Story 2.7: Handle Payment Failures with Retry & Notifications

As a subscribed user,
I want to be notified if my payment fails and have the system retry automatically,
So that I don't lose access due to temporary payment issues.

**Acceptance Criteria:**

**Given** A recurring payment fails (expired card, insufficient funds, etc.)
**When** Payment failure webhook (`invoice.payment_failed`) is received from Stripe
**Then** Subscription record is updated: status='PAYMENT_FAILED', failed_payment_attempts=attempt_count, payment_update_url=invoice `hosted_invoice_url` (V14__payment_failure_tracking.sql)
**And** I keep access while in PAYMENT_FAILED, with the banner, until suspension (decision 2A, Luiz, 2026-10-31)
**And** Email notification is sent immediately: "Pagamento falhou", with the link to pay the invoice in Stripe; the email does not include the failure reason (decision 3A); the email for the last attempt has no link
**And** Stripe retries the charge itself: 2 new attempts, 3 and 10 days after the failure (decision 1A; configured in Stripe Dashboard → Billing → Revenue recovery → Retries, checked at deploy). This is not NFR59, which covers webhook redelivery
**And** If a retry, or a payment through the link, succeeds (`invoice.paid`):
  - Subscription status returns to 'ACTIVE', attempts and link are cleared
  - next_billing_date is updated
  - Receipt "Pagamento confirmado" is sent (Story 2.6) and audit "payment_recovered" is logged
**And** If all retries fail, Stripe cancels the subscription (Dashboard setting) and `customer.subscription.deleted` arrives:
  - Subscription status changes to 'SUSPENDED'
  - Access is revoked (402)
  - The open invoice is voided in Stripe, best-effort, so the emailed link stops charging (decision 2A of the review)
  - Final email sent: "Assinatura suspensa", with a link to reactivate
**And** I can pay the invoice (with the saved card or a new one) at any time through "Atualizar pagamento"; a SUSPENDED user comes back only through Checkout (Story 2.5)
**And** Checkout during PAYMENT_FAILED returns 409 "pague a fatura pendente"; cancelling during PAYMENT_FAILED is immediate (Story 2.4)
**And** Failures are not written to `billing_transactions`
**And** Audit log records "payment_failed" (attempt_count, invoice_id), "payment_recovered" and "subscription_suspended"
**And** Dashboard shows "Pagamento falhou" banner with CTA "Atualizar pagamento", which opens the invoice page in Stripe

---

## Cross-epic: Account deletion (retro F1)

**Given** I have a subscription in Stripe and request account deletion (Story 1.7)
**When** The deletion request is committed
**Then** The subscription is cancelled in Stripe immediately (best-effort, after commit; failure logs an error for manual action), without waiting for the 30-day purge
**And** If it was PAYMENT_FAILED, the open invoice is voided
**And** The local record becomes 'EXPIRED' with cancellation_date, and audit "subscription_cancelled" is logged with reason "account_deletion"
**And** Cancellation in the purge remains as a safety net
