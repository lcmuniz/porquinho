-- V29: suporte à revisão da importação (Story 5.9)
-- APPROVED: o usuário aprovou a linha na revisão; CONFIRMED continua significando importação concluída (decisão 1A).
-- confirmed_by_import_id: importação que confirmou uma parcela futura criada por outra (decisão 3A). Confirmações
-- gravadas antes desta versão não têm a marca.
-- FK sem ON DELETE (como V25/V26): o purge LGPD apaga transactions antes de transaction_imports.

ALTER TABLE transactions DROP CONSTRAINT transactions_review_status_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_review_status_check
    CHECK (review_status IN ('PENDING', 'APPROVED', 'CONFIRMED'));

ALTER TABLE transactions ADD COLUMN confirmed_by_import_id UUID REFERENCES transaction_imports(id);

CREATE INDEX idx_transactions_confirmed_by_import_id ON transactions(confirmed_by_import_id);

COMMENT ON COLUMN transactions.review_status IS 'PENDING até a revisão; APPROVED se aprovada na revisão; CONFIRMED com a importação concluída.';
COMMENT ON COLUMN transactions.confirmed_by_import_id IS 'Importação que confirmou esta parcela futura, quando outra a criou (Story 5.9, decisão 3A).';
