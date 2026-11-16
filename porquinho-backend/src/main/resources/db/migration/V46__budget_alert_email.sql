-- V46: alerta de orçamento por e-mail (pré-Epic 10, AC adiado da Story 6.7).
-- Preferência global do usuário, ligada por padrão, e marca de dedup: no máximo um aviso por (usuário, categoria,
-- mês, nível). FKs sem ON DELETE (como V32): categoria é soft-delete e o purge LGPD apaga esta tabela antes de
-- categories e de users.

ALTER TABLE users ADD COLUMN budget_alert_email_enabled BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN users.budget_alert_email_enabled IS
    'Recebe e-mail quando uma categoria chega a 90% e a 100% do orçamento do mês. Vale para todas as categorias.';

CREATE TABLE budget_alert_notifications (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    month DATE NOT NULL,
    level SMALLINT NOT NULL CHECK (level IN (90, 100)),
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_budget_alert_notifications UNIQUE (user_id, category_id, month, level)
);

CREATE INDEX idx_budget_alert_notifications_user ON budget_alert_notifications(user_id);

-- Candidatos do job horário: busca por mês sem user_id (os índices da V32 começam por user_id ou category_id).
CREATE INDEX idx_budget_allocations_month_positive ON budget_allocations(month_year) WHERE allocated_amount > 0;

COMMENT ON TABLE budget_alert_notifications IS
    'Alertas de orçamento já enviados (dedup). Uma linha por usuário, categoria, mês e nível; gravada antes do envio.';
COMMENT ON COLUMN budget_alert_notifications.month IS 'Sempre o dia 1 do mês (America/Sao_Paulo).';
