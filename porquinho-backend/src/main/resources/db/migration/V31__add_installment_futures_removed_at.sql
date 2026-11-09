-- V31: remoção durável das parcelas futuras de uma compra (Story 5.12, decisão Q2)
-- Gravada nas parcelas ativas do grupo quando o usuário remove as futuras (compra estornada ou quitada): a próxima
-- importação do cartão não recria as futuras desse grupo e a parcela nova herda a marca.

ALTER TABLE transactions ADD COLUMN installment_futures_removed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN transactions.installment_futures_removed_at IS 'Quando o usuário removeu as parcelas futuras desta compra (Story 5.12, decisão Q2).';
