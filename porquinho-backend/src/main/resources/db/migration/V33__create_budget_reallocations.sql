-- V33: budget_reallocations (Story 6.5)
-- Registro de auditoria de cada dinheiro movido entre categorias filhas no mês; o histórico da 6.8 lê daqui.
-- Sem deleted_at: é auditoria, não se apaga nem se desfaz (só o purge LGPD apaga).
-- FKs sem ON DELETE (como V32): o purge LGPD apaga esta tabela antes de categories e de users.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE budget_reallocations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    from_category_id UUID NOT NULL REFERENCES categories(id),
    to_category_id UUID NOT NULL REFERENCES categories(id),
    month_year DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT budget_reallocations_amount_check CHECK (amount > 0),
    CONSTRAINT budget_reallocations_distinct_check CHECK (from_category_id <> to_category_id)
);

CREATE INDEX idx_budget_reallocations_user_month ON budget_reallocations(user_id, month_year);
CREATE INDEX idx_budget_reallocations_from_category ON budget_reallocations(from_category_id);
CREATE INDEX idx_budget_reallocations_to_category ON budget_reallocations(to_category_id);

COMMENT ON TABLE budget_reallocations IS 'Dinheiro movido entre categorias no orçamento do mês (Story 6.5).';
COMMENT ON COLUMN budget_reallocations.month_year IS 'Sempre o dia 1 do mês.';
