-- V9: Create subscriptions table (Story 2.1 - trial gratuito de 30 dias)
-- Um registro por usuário durante toda a vida (UNIQUE user_id). Upgrade/cancelamento
-- (Stories 2.2+) atualizam a linha, o que também impede reiniciar o trial.
-- Sem índice parcial (H2 não suporta WHERE em índice); UNIQUE(user_id) já cria o índice.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2 dos testes.

CREATE TABLE subscriptions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2 (tests)
    user_id UUID NOT NULL REFERENCES users(id),
    plan_type VARCHAR(20) NOT NULL CHECK (plan_type IN ('TRIAL', 'MONTHLY')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    trial_start_date TIMESTAMP WITH TIME ZONE,
    trial_end_date TIMESTAMP WITH TIME ZONE,
    next_billing_date TIMESTAMP WITH TIME ZONE,
    payment_method_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT subscriptions_user_id_unique UNIQUE (user_id)
);

-- Backfill: usuários existentes ganham trial de 30 dias a partir da data da migration
INSERT INTO subscriptions (user_id, plan_type, status, trial_start_date, trial_end_date)
SELECT id, 'TRIAL', 'ACTIVE', NOW(), NOW() + INTERVAL '30' DAY
FROM users
WHERE deleted_at IS NULL
  AND id NOT IN (SELECT user_id FROM subscriptions);

COMMENT ON TABLE subscriptions IS 'Assinatura do usuário (trial ou mensal). Uma linha por usuário.';
COMMENT ON COLUMN subscriptions.trial_end_date IS 'Fim do trial. Expiração é calculada na leitura (job diário na Story 2.3).';
COMMENT ON COLUMN subscriptions.payment_method_id IS 'Preenchido pela Story 2.2 (Stripe). NULL durante o trial.';
