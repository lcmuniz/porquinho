-- V17: Create credit_cards table (Story 3.3 - cartões de crédito no setup do onboarding)
-- Base dos cartões do Epic 4 e da importação. Soft delete via deleted_at.
-- Sem índice parcial (H2 não suporta WHERE em índice): índice simples em user_id.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE credit_cards (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    card_name VARCHAR(100) NOT NULL,
    bank_name VARCHAR(100),
    card_limit DECIMAL(15,2) NOT NULL,
    due_day INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT credit_cards_due_day_check CHECK (due_day BETWEEN 1 AND 31)
);

CREATE INDEX idx_credit_cards_user_id ON credit_cards(user_id);

COMMENT ON TABLE credit_cards IS 'Cartões de crédito do usuário (Story 3.3; ampliada no Epic 4). Soft delete via deleted_at.';
COMMENT ON COLUMN credit_cards.card_name IS 'Nome dado pelo usuário, ex.: "Nubank Roxinho".';
COMMENT ON COLUMN credit_cards.bank_name IS 'Banco emissor em texto livre (opcional), ex.: "Nubank".';
COMMENT ON COLUMN credit_cards.card_limit IS 'Limite total do cartão (>= 0).';
COMMENT ON COLUMN credit_cards.due_day IS 'Dia do mês em que a fatura vence (1 a 31).';
COMMENT ON COLUMN credit_cards.deleted_at IS 'Preenchido quando o usuário remove o cartão (soft delete).';
