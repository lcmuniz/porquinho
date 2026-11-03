-- V21: Add deleted_by to accounts and credit_cards (Story 4.4 - remover e restaurar; decisão 2A)
-- A remoção grava deleted_by = user_id junto com deleted_at; a restauração zera os dois.

ALTER TABLE accounts ADD COLUMN deleted_by UUID REFERENCES users(id);
ALTER TABLE credit_cards ADD COLUMN deleted_by UUID REFERENCES users(id);

-- Índices na FK: apagar um usuário não varre as tabelas inteiras para conferir deleted_by
CREATE INDEX idx_accounts_deleted_by ON accounts(deleted_by);
CREATE INDEX idx_credit_cards_deleted_by ON credit_cards(deleted_by);

COMMENT ON COLUMN accounts.deleted_by IS 'Usuário que removeu a conta (soft delete). NULL enquanto a conta está ativa.';
COMMENT ON COLUMN credit_cards.deleted_by IS 'Usuário que removeu o cartão (soft delete). NULL enquanto o cartão está ativo.';
