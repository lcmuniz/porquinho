-- V27: transaction_imports.transfers_found (Story 5.5 - detecção de duplicatas)
-- Lançamentos do arquivo que correspondem a uma transferência entre contas próprias já registrada (Story 4.5,
-- decisão D4 do Epic 5): não são gravados e são contados à parte de duplicates_found.

ALTER TABLE transaction_imports ADD COLUMN transfers_found INTEGER NOT NULL DEFAULT 0;

ALTER TABLE transaction_imports ADD CONSTRAINT transaction_imports_transfers_found_check CHECK (transfers_found >= 0);

COMMENT ON COLUMN transaction_imports.transfers_found IS
    'Lançamentos ignorados por corresponderem a uma transferência já registrada (Story 5.5, D4).';
