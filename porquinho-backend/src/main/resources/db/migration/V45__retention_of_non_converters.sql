-- V45: retenção de quem não converte (LGPD). Fim do acesso, avisos de purge e hash do e-mail que sobrevive ao purge.
ALTER TABLE subscriptions
    ADD COLUMN access_ended_at TIMESTAMPTZ NULL,
    ADD COLUMN retention_warning_7d_sent_at TIMESTAMPTZ NULL,
    ADD COLUMN retention_warning_1d_sent_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN subscriptions.access_ended_at IS
    'Quando o acesso acabou (EXPIRED/SUSPENDED). Nulo: o fim vem de trial_end_date (TRIAL) ou next_billing_date (MONTHLY CANCELLED). Purge em fim + 30 dias.';

-- Quem já está sem acesso no deploy conta os 30 dias a partir de agora (decisão 2026-11-16).
UPDATE subscriptions SET access_ended_at = now()
WHERE status IN ('EXPIRED', 'SUSPENDED')
   OR (status = 'CANCELLED' AND (next_billing_date IS NULL OR next_billing_date <= now()))
   OR (status = 'ACTIVE' AND plan_type = 'TRIAL' AND (trial_end_date IS NULL OR trial_end_date <= now()));

ALTER TABLE account_deletions
    ADD COLUMN reason VARCHAR(20) NOT NULL DEFAULT 'USER_REQUEST'
        CHECK (reason IN ('USER_REQUEST', 'RETENTION')),
    ADD COLUMN email_hash VARCHAR(64) NULL;

CREATE INDEX idx_account_deletions_email_hash ON account_deletions(email_hash);

COMMENT ON COLUMN account_deletions.reason IS
    'USER_REQUEST: exclusão pedida pelo usuário (Story 1.7). RETENTION: purge de quem ficou 30 dias sem acesso.';
COMMENT ON COLUMN account_deletions.email_hash IS
    'SHA-256 hex do e-mail (trim, minúsculas). Sobrevive ao purge: o recadastro com o mesmo e-mail nasce sem trial.';

-- Pedidos de exclusão ainda pendentes: o usuário (soft-deleted) ainda está em users. Mesmo hash do Java.
UPDATE account_deletions d SET email_hash = encode(sha256(convert_to(lower(trim(u.email)), 'UTF8')), 'hex')
FROM users u WHERE u.id = d.user_id AND d.email_hash IS NULL;
