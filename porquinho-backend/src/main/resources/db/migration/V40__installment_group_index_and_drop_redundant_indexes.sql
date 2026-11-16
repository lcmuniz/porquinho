-- V40: índice das parcelas e limpeza de índices redundantes (análise 16/11)
-- As consultas de parcelas (TransactionRepository) filtram por user_id e installment_group_id = / IN (...), o que só
-- casa com linhas que têm grupo: o índice parcial cobre essas e fica pequeno (a maioria das transações não é parcela).
CREATE INDEX idx_transactions_user_installment_group ON transactions(user_id, installment_group_id)
    WHERE installment_group_id IS NOT NULL;

-- Duplicado da constraint users_email_unique (V2), que segue garantindo a unicidade com o próprio índice
DROP INDEX IF EXISTS idx_users_email;
-- Duplicado do índice da coluna download_token UUID UNIQUE (V7)
DROP INDEX IF EXISTS idx_data_exports_download_token;
-- Prefixo de idx_transactions_user_date; a V38 já o removeu, o IF EXISTS cobre bancos fora de ordem
DROP INDEX IF EXISTS idx_transactions_user_id;
-- users.locked_until não é usado: o bloqueio de login vive no Redis (AccountLockService)
DROP INDEX IF EXISTS idx_users_locked_until;
