-- V25: Create transaction_imports table (Story 5.1 - envio de extrato OFX)
-- Uma linha por arquivo enviado. O destino é uma conta OU um cartão do usuário (decisões D1/D2 do Epic 5).
-- O conteúdo do arquivo não é gravado: só nome, tipo e tamanho. Nesta story a importação fica em PROCESSING;
-- a leitura dos lançamentos e os demais status chegam nas Stories 5.3–5.10.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE transaction_imports (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    account_id UUID REFERENCES accounts(id),
    credit_card_id UUID REFERENCES credit_cards(id),
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    bank_name VARCHAR(100),
    file_size_kb INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    total_transactions INTEGER NOT NULL DEFAULT 0,
    imported_transactions INTEGER NOT NULL DEFAULT 0,
    duplicates_found INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT transaction_imports_status_check
        CHECK (status IN ('PROCESSING', 'REVIEW', 'COMPLETED', 'FAILED', 'DISCARDED')),
    CONSTRAINT transaction_imports_file_type_check CHECK (file_type IN ('OFX', 'CSV')),
    CONSTRAINT transaction_imports_one_target_check
        CHECK ((account_id IS NOT NULL AND credit_card_id IS NULL) OR (account_id IS NULL AND credit_card_id IS NOT NULL)),
    CONSTRAINT transaction_imports_file_size_check CHECK (file_size_kb >= 0)
);

-- Composto: serve a FK de user_id e a lista "Importações recentes" (mais recentes primeiro)
CREATE INDEX idx_transaction_imports_user_created ON transaction_imports(user_id, created_at DESC);
CREATE INDEX idx_transaction_imports_account_id ON transaction_imports(account_id);
CREATE INDEX idx_transaction_imports_credit_card_id ON transaction_imports(credit_card_id);

COMMENT ON TABLE transaction_imports IS 'Extratos enviados pelo usuário (Story 5.1). O arquivo em si nunca é guardado.';
COMMENT ON COLUMN transaction_imports.account_id IS 'Conta de destino (exclusivo com credit_card_id).';
COMMENT ON COLUMN transaction_imports.credit_card_id IS 'Cartão de destino (exclusivo com account_id).';
COMMENT ON COLUMN transaction_imports.file_name IS 'Nome do arquivo enviado, sem caminho (até 255 caracteres).';
COMMENT ON COLUMN transaction_imports.file_type IS 'OFX ou CSV.';
COMMENT ON COLUMN transaction_imports.bank_name IS 'Banco detectado (Story 5.3); NULL até a detecção.';
COMMENT ON COLUMN transaction_imports.file_size_kb IS 'Tamanho do arquivo em KB (arredondado para cima).';
COMMENT ON COLUMN transaction_imports.status IS 'PROCESSING, REVIEW, COMPLETED, FAILED ou DISCARDED.';
COMMENT ON COLUMN transaction_imports.skipped_rows IS 'Linhas que não puderam ser lidas (Story 5.4).';
COMMENT ON COLUMN transaction_imports.error_message IS 'Motivo da falha, em pt-BR, quando status = FAILED.';
