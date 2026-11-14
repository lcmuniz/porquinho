-- V37: goal_progress_history (Story 8.8)
-- Snapshots do acumulado da meta: na criação, em cada aporte (contribution_amount preenchido) e todo dia 1º
-- (job mensal, sem aporte). Só inserção: os snapshots não são editados nem apagados (exceto pelo purge LGPD).
-- FKs sem ON DELETE (como V34): o purge LGPD apaga esta tabela antes de goals e de users.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE goal_progress_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    goal_id UUID NOT NULL REFERENCES goals(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount_snapshot DECIMAL(15,2) NOT NULL,
    snapshot_date DATE NOT NULL,
    contribution_amount DECIMAL(15,2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goal_progress_history_user_goal_date ON goal_progress_history(user_id, goal_id, snapshot_date);

-- Backfill das metas existentes: 1 snapshot inicial na data de criação (current_amount - aportes da V34, sem aporte)
INSERT INTO goal_progress_history (goal_id, user_id, amount_snapshot, snapshot_date, contribution_amount, created_at)
SELECT g.id, g.user_id,
       g.current_amount - COALESCE((SELECT SUM(a.contributed_amount) FROM goal_allocations a WHERE a.goal_id = g.id), 0),
       CAST(g.created_at AT TIME ZONE 'America/Sao_Paulo' AS DATE), NULL, g.created_at
FROM goals g;

-- ... e 1 entrada por mês com aporte, no dia 1º, com o total corrente (base + aportes acumulados até o mês)
INSERT INTO goal_progress_history (goal_id, user_id, amount_snapshot, snapshot_date, contribution_amount, created_at)
SELECT a.goal_id, a.user_id,
       g.current_amount - (SELECT SUM(t.contributed_amount) FROM goal_allocations t WHERE t.goal_id = a.goal_id)
           + SUM(a.contributed_amount) OVER (PARTITION BY a.goal_id ORDER BY a.month_year),
       -- GREATEST: no mês da criação, o dia 1º viria antes do snapshot inicial
       GREATEST(a.month_year, CAST(g.created_at AT TIME ZONE 'America/Sao_Paulo' AS DATE)),
       a.contributed_amount, a.created_at
FROM goal_allocations a JOIN goals g ON g.id = a.goal_id
WHERE a.contributed_amount > 0;

COMMENT ON TABLE goal_progress_history IS 'Snapshots do acumulado das metas (Story 8.8).';
COMMENT ON COLUMN goal_progress_history.snapshot_date IS 'Data do snapshot em America/Sao_Paulo.';
COMMENT ON COLUMN goal_progress_history.contribution_amount IS 'Valor do aporte; nulo para snapshot de criação ou mensal.';
