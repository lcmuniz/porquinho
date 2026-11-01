-- V14: Falhas de pagamento (Story 2.7)
-- Novos status: PAYMENT_FAILED (cobrança recusada, o Stripe ainda vai tentar de novo; acesso mantido)
-- e SUSPENDED (todas as tentativas falharam e o Stripe cancelou a assinatura; sem acesso).
-- O CHECK inline da V9 recebeu o nome gerado pelo PostgreSQL: <tabela>_<coluna>_check.
-- Sem IF EXISTS de propósito: se o nome não bater, a migration falha (e é revertida) em vez de
-- deixar o CHECK antigo bloqueando os novos status.

ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_status_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check
    CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'PAYMENT_FAILED', 'SUSPENDED'));

ALTER TABLE subscriptions ADD COLUMN failed_payment_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN payment_update_url VARCHAR(1024);

COMMENT ON COLUMN subscriptions.failed_payment_attempts IS 'attempt_count da última invoice.payment_failed. Zerado quando a fatura é paga.';
COMMENT ON COLUMN subscriptions.payment_update_url IS 'hosted_invoice_url da fatura que falhou (o usuário paga ou troca o cartão ali). NULL fora de PAYMENT_FAILED.';
