-- V19: Create goals table (Story 3.8 - meta inicial de Reserva de Emergência no onboarding)
-- Base das metas do Epic 8 (aportes e goal_allocations chegam lá). Soft delete via deleted_at.
-- Sem índice parcial (H2 não suporta WHERE em índice): índice simples em user_id.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE goals (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    goal_name VARCHAR(100) NOT NULL,
    description TEXT,
    target_amount DECIMAL(15,2) NOT NULL,
    current_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    target_date DATE,
    monthly_allocation DECIMAL(15,2),
    is_completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT goals_target_amount_check CHECK (target_amount > 0),
    CONSTRAINT goals_monthly_allocation_check CHECK (monthly_allocation IS NULL OR monthly_allocation >= 0)
);

CREATE INDEX idx_goals_user_id ON goals(user_id);

COMMENT ON TABLE goals IS 'Metas financeiras do usuário (Story 3.8; ampliada no Epic 8). Soft delete via deleted_at.';
COMMENT ON COLUMN goals.goal_name IS 'Nome da meta, ex.: "Reserva de Emergência".';
COMMENT ON COLUMN goals.description IS 'Descrição livre (opcional; usada a partir da Story 8.1).';
COMMENT ON COLUMN goals.target_amount IS 'Valor-alvo da meta (> 0).';
COMMENT ON COLUMN goals.current_amount IS 'Valor já acumulado (começa em 0; aportes chegam no Epic 8).';
COMMENT ON COLUMN goals.target_date IS 'Data-alvo (opcional).';
COMMENT ON COLUMN goals.monthly_allocation IS 'Aporte mensal planejado (opcional, >= 0).';
COMMENT ON COLUMN goals.is_completed IS 'Verdadeiro quando a meta foi atingida.';
COMMENT ON COLUMN goals.deleted_at IS 'Preenchido quando o usuário remove a meta (soft delete).';
