-- V12: Data do cancelamento self-service da assinatura mensal (Story 2.4)
-- Preenchida quando o usuário cancela (status CANCELLED). O acesso continua até next_billing_date.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL), mesmo padrão da V9/V11.

ALTER TABLE subscriptions ADD COLUMN cancellation_date TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN subscriptions.cancellation_date IS 'Quando o usuário pediu o cancelamento (fim do período no Stripe). NULL = nunca cancelou.';
