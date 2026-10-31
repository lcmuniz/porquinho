-- V13: Histórico de pagamentos da assinatura mensal (Story 2.6 - pagamentos recorrentes)
-- Uma linha por fatura paga no Stripe (webhook invoice.paid). Quem cobra é o Stripe; aqui só se registra.
-- stripe_transaction_id = id da fatura (in_...), UNIQUE: webhook repetido não grava de novo.
-- status: só PAID por enquanto; a Story 2.7 acrescenta FAILED.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL), mesmo padrão da V9.

CREATE TABLE billing_transactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2 (tests)
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PAID')),
    payment_method VARCHAR(255),
    stripe_transaction_id VARCHAR(255) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT billing_transactions_stripe_transaction_id_unique UNIQUE (stripe_transaction_id)
);

CREATE INDEX idx_billing_transactions_subscription_id ON billing_transactions(subscription_id);
CREATE INDEX idx_billing_transactions_user_id ON billing_transactions(user_id);

COMMENT ON TABLE billing_transactions IS 'Pagamentos da assinatura mensal confirmados pelo Stripe (invoice.paid). Apagada no purge da conta.';
COMMENT ON COLUMN billing_transactions.amount IS 'Valor pago em reais (amount_paid do Stripe / 100).';
COMMENT ON COLUMN billing_transactions.currency IS 'Moeda ISO 4217 em minúsculas, como o Stripe envia (brl).';
COMMENT ON COLUMN billing_transactions.payment_method IS 'payment_method_id tokenizado do Stripe (pm_...). Nunca dados de cartão.';
COMMENT ON COLUMN billing_transactions.stripe_transaction_id IS 'Id da fatura no Stripe (in_...). Chave de idempotência do webhook.';
COMMENT ON COLUMN billing_transactions.paid_at IS 'Quando o Stripe confirmou o pagamento (status_transitions.paid_at).';
