-- V22: Create transfers table (Story 4.5 - transferências entre contas próprias)
-- Uma linha por transferência: debita a conta de origem e credita a de destino na mesma transação,
-- com uma linha 'transfer' no account_balance_history de cada conta. O V12 citado no épico já existe.
-- O id vem do cliente (chave de idempotência): repetir o envio devolve a mesma transferência.
-- Sem edição nem exclusão pela interface por enquanto; deleted_at fica pronto para o soft delete.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE transfers (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    from_account_id UUID NOT NULL REFERENCES accounts(id),
    to_account_id UUID NOT NULL REFERENCES accounts(id),
    amount DECIMAL(15,2) NOT NULL,
    transfer_date DATE NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT transfers_amount_check CHECK (amount > 0),
    CONSTRAINT transfers_different_accounts_check CHECK (from_account_id <> to_account_id)
);

-- Composto: serve a FK de user_id e a lista "Transferências recentes" (mais recentes primeiro)
CREATE INDEX idx_transfers_user_date ON transfers(user_id, transfer_date DESC, created_at DESC);
CREATE INDEX idx_transfers_from_account_id ON transfers(from_account_id);
CREATE INDEX idx_transfers_to_account_id ON transfers(to_account_id);

COMMENT ON TABLE transfers IS 'Transferências entre contas do próprio usuário (Story 4.5). Não afetam categorias nem orçamento.';
COMMENT ON COLUMN transfers.from_account_id IS 'Conta de origem (debitada).';
COMMENT ON COLUMN transfers.to_account_id IS 'Conta de destino (creditada). Sempre diferente da origem.';
COMMENT ON COLUMN transfers.amount IS 'Valor transferido (> 0).';
COMMENT ON COLUMN transfers.transfer_date IS 'Data da transferência informada pelo usuário (não pode ser futura).';
COMMENT ON COLUMN transfers.description IS 'Descrição livre (opcional, até 200 caracteres).';
COMMENT ON COLUMN transfers.deleted_at IS 'Preenchido se a transferência for removida (soft delete; ainda sem uso).';
COMMENT ON COLUMN transfers.id IS 'Gerado pelo cliente (chave de idempotência): reenviar o mesmo id não duplica a transferência.';

-- Vínculo das linhas 'transfer' do histórico com a transferência (o gráfico da Story 4.7 junta pela transfer_date)
ALTER TABLE account_balance_history
    ADD COLUMN transfer_id UUID REFERENCES transfers(id) ON DELETE SET NULL;
CREATE INDEX idx_account_balance_history_transfer_id ON account_balance_history(transfer_id);
COMMENT ON COLUMN account_balance_history.transfer_id IS 'Transferência que gerou a linha (só em created_by_event = transfer; NULL nos outros eventos).';
