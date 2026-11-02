-- V16: Create accounts table (Story 3.2 - contas bancárias no setup do onboarding)
-- Base das contas do Epic 4 e da importação do Epic 5. Soft delete via deleted_at.
-- Sem índice parcial (H2 não suporta WHERE em índice): índice simples em user_id.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE accounts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    account_name VARCHAR(100) NOT NULL,
    bank_name VARCHAR(100),
    account_type VARCHAR(20) NOT NULL,
    initial_balance DECIMAL(15,2) NOT NULL,
    current_balance DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT accounts_account_type_check CHECK (account_type IN ('CHECKING', 'SAVINGS'))
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

COMMENT ON TABLE accounts IS 'Contas bancárias do usuário (Story 3.2; ampliada no Epic 4). Soft delete via deleted_at.';
COMMENT ON COLUMN accounts.account_name IS 'Nome dado pelo usuário, ex.: "Nubank" ou "Minha Conta Principal".';
COMMENT ON COLUMN accounts.bank_name IS 'Banco em texto livre (opcional), ex.: "Itaú".';
COMMENT ON COLUMN accounts.account_type IS 'CHECKING (conta corrente) ou SAVINGS (poupança).';
COMMENT ON COLUMN accounts.initial_balance IS 'Saldo informado na criação. Pode ser negativo (cheque especial).';
COMMENT ON COLUMN accounts.current_balance IS 'Saldo atual. Igual ao inicial na criação; atualizado pelas transações (Epics 4 e 5).';
COMMENT ON COLUMN accounts.deleted_at IS 'Preenchido quando o usuário remove a conta (soft delete).';
