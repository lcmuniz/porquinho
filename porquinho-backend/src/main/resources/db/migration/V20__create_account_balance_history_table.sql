-- V20: Create account_balance_history table (Story 4.3 - editar contas; decisão A)
-- Uma linha por mudança do saldo atual: 'initial' na criação da conta, 'edit' na edição manual.
-- 'transaction', 'reconciliation' e 'transfer' chegam com os Epics 4 e 5; o gráfico é a Story 4.7.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE account_balance_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,  -- snapshot never blocks deleting the account
    user_id UUID NOT NULL REFERENCES users(id),
    balance_snapshot DECIMAL(15,2) NOT NULL,
    snapshot_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_event VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT account_balance_history_event_check
        CHECK (created_by_event IN ('transaction', 'reconciliation', 'edit', 'transfer', 'initial'))
);

-- Composto: serve a FK e a consulta por conta em ordem de data do gráfico (Story 4.7)
CREATE INDEX idx_account_balance_history_account_date ON account_balance_history(account_id, snapshot_date);
CREATE INDEX idx_account_balance_history_user_id ON account_balance_history(user_id);

COMMENT ON TABLE account_balance_history IS 'Histórico do saldo atual das contas (Story 4.3; gráfico na Story 4.7).';
COMMENT ON COLUMN account_balance_history.balance_snapshot IS 'Saldo atual da conta depois do evento. Pode ser negativo.';
COMMENT ON COLUMN account_balance_history.snapshot_date IS 'Momento do evento que gerou o saldo.';
COMMENT ON COLUMN account_balance_history.created_by_event IS 'initial (criação), edit (edição manual), transaction, reconciliation ou transfer.';

-- Backfill: uma linha 'initial' por conta já existente (removidas incluídas), com o saldo e a data da criação
INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, created_by_event)
SELECT id, user_id, initial_balance, created_at, 'initial' FROM accounts;
