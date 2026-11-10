-- V32: budget_allocations (Story 6.3)
-- Quanto o usuário pretende gastar em cada categoria filha no mês. Uma linha por categoria e mês; categoria sem valor
-- não tem linha ("Sem orçamento definido"). O gasto e o restante são calculados a cada leitura (sem coluna).
-- Sem deleted_at: é configuração, não lançamento financeiro (como user_classification_patterns); limpar apaga a linha.
-- FKs sem ON DELETE (como V30): o purge LGPD apaga esta tabela antes de categories e de users.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE budget_allocations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    month_year DATE NOT NULL,
    allocated_amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT budget_allocations_allocated_amount_check CHECK (allocated_amount >= 0)
);

CREATE UNIQUE INDEX idx_budget_allocations_user_category_month
    ON budget_allocations(user_id, category_id, month_year);
CREATE INDEX idx_budget_allocations_user_month ON budget_allocations(user_id, month_year);
CREATE INDEX idx_budget_allocations_category ON budget_allocations(category_id);

COMMENT ON TABLE budget_allocations IS 'Orçamento mensal por categoria filha (Story 6.3).';
COMMENT ON COLUMN budget_allocations.month_year IS 'Sempre o dia 1 do mês.';
COMMENT ON COLUMN budget_allocations.allocated_amount IS 'Valor alocado no mês; 0 é uma alocação válida.';
