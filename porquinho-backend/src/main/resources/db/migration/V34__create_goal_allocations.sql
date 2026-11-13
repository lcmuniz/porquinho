-- V34: goal_allocations (Story 8.2)
-- Quanto o usuário alocou e aportou em cada meta no mês. Uma linha por meta e mês, criada ao editar a alocação ou no
-- primeiro aporte do mês. O épico cita V21, mas esse número já está em uso.
-- FKs sem ON DELETE (como V32): o purge LGPD apaga esta tabela antes de goals e de users.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE goal_allocations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    goal_id UUID NOT NULL REFERENCES goals(id),
    month_year DATE NOT NULL,
    allocated_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    contributed_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT goal_allocations_allocated_amount_check CHECK (allocated_amount >= 0),
    CONSTRAINT goal_allocations_contributed_amount_check CHECK (contributed_amount >= 0)
);

CREATE UNIQUE INDEX idx_goal_allocations_goal_month ON goal_allocations(goal_id, month_year);
CREATE INDEX idx_goal_allocations_user_month ON goal_allocations(user_id, month_year);

COMMENT ON TABLE goal_allocations IS 'Alocação e aportes mensais por meta (Story 8.2).';
COMMENT ON COLUMN goal_allocations.month_year IS 'Sempre o dia 1 do mês (America/Sao_Paulo).';
COMMENT ON COLUMN goal_allocations.allocated_amount IS 'Valor alocado à meta no mês; 0 é uma alocação válida.';
COMMENT ON COLUMN goal_allocations.contributed_amount IS 'Soma dos aportes feitos na meta no mês.';
