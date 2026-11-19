-- V53: transação importada vinculada a uma transferência registrada depois (checklist de deploy 4.1, opção a)
-- Quem importa um extrato com uma TED e depois registra a mesma TED como transferência (Story 4.5) não deve ter o valor
-- contado duas vezes. Ao registrar a transferência, a linha importada que casa (regra D4 da Story 5.5) sai do saldo
-- (affects_balance = false, como uma linha absorvida por conciliação) e guarda aqui a transferência que a absorveu.
-- Uma exclusão ou edição futura da transferência usa este vínculo para devolver a linha ao saldo.

ALTER TABLE transactions ADD COLUMN absorbed_by_transfer_id UUID REFERENCES transfers(id);

-- Sem ação no ON DELETE, como as outras FKs de transactions: a exclusão de conta apaga as transações antes das
-- transferências. O índice serve a checagem da FK e a busca do vínculo pela transferência.
CREATE INDEX idx_transactions_absorbed_by_transfer_id ON transactions(absorbed_by_transfer_id)
    WHERE absorbed_by_transfer_id IS NOT NULL;

COMMENT ON COLUMN transactions.absorbed_by_transfer_id IS 'Transferência registrada depois da importação que já lançou este valor no saldo; a linha fica fora do saldo enquanto o vínculo existir.';
