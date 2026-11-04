-- V23: Create reconciliation_history table (Story 4.6 - reconciliar o saldo da conta com o banco)
-- Uma linha por reconciliação: o saldo que o porquinho tinha, o saldo informado do banco e a diferença
-- (calculada no servidor). Decisão A: sem transação de ajuste; este evento é o registro do ajuste.
-- Diferença 0 também gera linha (o usuário conferiu e bateu).
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE reconciliation_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    previous_balance DECIMAL(15,2) NOT NULL,
    bank_balance DECIMAL(15,2) NOT NULL,
    difference DECIMAL(15,2) NOT NULL,
    reason VARCHAR(200),
    reconciled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Composto: serve a FK de account_id e a lista das reconciliações da conta (mais recentes primeiro)
CREATE INDEX idx_reconciliation_history_account_date ON reconciliation_history(account_id, reconciled_at DESC);
CREATE INDEX idx_reconciliation_history_user_id ON reconciliation_history(user_id);

COMMENT ON TABLE reconciliation_history IS 'Reconciliações do saldo das contas com o banco (Story 4.6). Sem transação de ajuste (decisão A).';
COMMENT ON COLUMN reconciliation_history.previous_balance IS 'Saldo atual da conta no porquinho antes da reconciliação.';
COMMENT ON COLUMN reconciliation_history.bank_balance IS 'Saldo informado pelo usuário a partir do banco; vira o novo saldo atual. Pode ser negativo.';
COMMENT ON COLUMN reconciliation_history.difference IS 'bank_balance - previous_balance, calculada no servidor. Pode ser 0.';
COMMENT ON COLUMN reconciliation_history.reason IS 'Motivo livre (opcional, até 200 caracteres).';
COMMENT ON COLUMN reconciliation_history.reconciled_at IS 'Momento em que o usuário conferiu o saldo com o banco.';

ALTER TABLE accounts ADD COLUMN last_reconciliation_date TIMESTAMP WITH TIME ZONE;
COMMENT ON COLUMN accounts.last_reconciliation_date IS 'Momento da última reconciliação com o banco (Story 4.6); NULL se nunca reconciliada.';
