-- V10: IDs do Stripe na assinatura (Story 2.2 - assinatura mensal via Stripe Checkout)
-- Preenchidos pelo webhook checkout.session.completed. NULL durante o trial.

ALTER TABLE subscriptions ADD COLUMN stripe_customer_id VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN stripe_subscription_id VARCHAR(255);
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_stripe_subscription_id_unique UNIQUE (stripe_subscription_id);

COMMENT ON COLUMN subscriptions.stripe_customer_id IS 'Customer do Stripe (cus_...). Preenchido na ativação do plano mensal.';
COMMENT ON COLUMN subscriptions.stripe_subscription_id IS 'Subscription do Stripe (sub_...). Chave para webhooks das Stories 2.4-2.7.';
