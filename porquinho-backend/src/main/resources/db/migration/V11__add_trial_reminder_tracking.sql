-- V11: Controle de envio dos lembretes de fim de trial (Story 2.3)
-- Cada coluna é preenchida uma única vez pelo job diário: garante que cada lembrete
-- saia no máximo uma vez por usuário, mesmo com o job rodando duas vezes ou após restart.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL), mesmo padrão da V9.

ALTER TABLE subscriptions ADD COLUMN trial_reminder_7d_sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscriptions ADD COLUMN trial_reminder_1d_sent_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN subscriptions.trial_reminder_7d_sent_at IS 'Quando o lembrete "trial termina em 7 dias" foi enviado. NULL = não enviado.';
COMMENT ON COLUMN subscriptions.trial_reminder_1d_sent_at IS 'Quando o lembrete "trial termina amanhã" foi enviado. NULL = não enviado.';
