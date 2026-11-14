-- V38: índices do dashboard (retro Epic 7 A2, B5 e B6)
-- As somas das Camadas 1 e 2 filtram transactions por usuário e data; a curva de patrimônio (7.4) lê os snapshots
-- do usuário a partir do corte. Os índices só por user_id ficam cobertos pelo prefixo dos novos.

CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date);
DROP INDEX idx_transactions_user_id;

CREATE INDEX idx_account_balance_history_user_date ON account_balance_history(user_id, snapshot_date);
DROP INDEX idx_account_balance_history_user_id;
