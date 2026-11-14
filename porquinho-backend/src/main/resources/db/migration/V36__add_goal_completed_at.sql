-- V36: goals.completed_at (Story 8.7)
-- Data em que a meta foi concluída (automática no aporte que atinge o alvo, ou manual). Nula enquanto ativa;
-- volta a nula ao reabrir. Backfill: metas já concluídas recebem updated_at (melhor aproximação disponível).
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

ALTER TABLE goals ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

UPDATE goals SET completed_at = updated_at WHERE is_completed = true;

COMMENT ON COLUMN goals.completed_at IS 'Quando a meta foi concluída; nula se ativa (Story 8.7).';
