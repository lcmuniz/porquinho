-- V26: Create transactions table (Story 5.4 - leitura das transações do OFX importado)
-- Cada lançamento pertence a uma conta OU a um cartão do usuário (decisão D1 do Epic 5). Valores sempre positivos,
-- com o sentido em transaction_type. Transações importadas nascem PENDING e não entram em listas, totais ou saldos
-- até a importação ser concluída (Story 5.10). Pix, parcelas e classificação chegam nas Stories 5.6–5.8.
-- FKs sem ON DELETE (como V25): o purge LGPD apaga transactions antes de imports, contas, cartões e categorias.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE transactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    account_id UUID REFERENCES accounts(id),
    credit_card_id UUID REFERENCES credit_cards(id),
    import_id UUID REFERENCES transaction_imports(id),
    external_id VARCHAR(255),
    transaction_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    original_description VARCHAR(500) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    kind VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    category_id UUID REFERENCES categories(id),
    classification_source VARCHAR(10) NOT NULL DEFAULT 'NONE',
    classification_confidence INTEGER,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    affects_balance BOOLEAN NOT NULL DEFAULT FALSE,
    is_pix BOOLEAN NOT NULL DEFAULT FALSE,
    pix_details JSONB,
    is_installment BOOLEAN NOT NULL DEFAULT FALSE,
    installment_number INTEGER,
    installment_total INTEGER,
    installment_group_id UUID,
    is_future_installment BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT transactions_one_target_check
        CHECK ((account_id IS NOT NULL AND credit_card_id IS NULL) OR (account_id IS NULL AND credit_card_id IS NOT NULL)),
    CONSTRAINT transactions_amount_check CHECK (amount > 0),
    CONSTRAINT transactions_type_check CHECK (transaction_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT transactions_kind_check CHECK (kind IN ('REGULAR', 'CARD_PAYMENT')),
    CONSTRAINT transactions_classification_source_check
        CHECK (classification_source IN ('AI', 'PATTERN', 'USER', 'NONE')),
    CONSTRAINT transactions_classification_confidence_check
        CHECK (classification_confidence IS NULL OR (classification_confidence >= 0 AND classification_confidence <= 100)),
    CONSTRAINT transactions_review_status_check CHECK (review_status IN ('PENDING', 'CONFIRMED'))
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_import_id ON transactions(import_id);
-- Destino + data: listas e extratos por conta ou cartão (e a busca de duplicatas da Story 5.5)
CREATE INDEX idx_transactions_account_date ON transactions(account_id, transaction_date);
CREATE INDEX idx_transactions_credit_card_date ON transactions(credit_card_id, transaction_date);

COMMENT ON TABLE transactions IS 'Lançamentos de contas e cartões (Story 5.4). Importados nascem PENDING.';
COMMENT ON COLUMN transactions.account_id IS 'Conta do lançamento (exclusivo com credit_card_id).';
COMMENT ON COLUMN transactions.credit_card_id IS 'Cartão do lançamento (exclusivo com account_id).';
COMMENT ON COLUMN transactions.import_id IS 'Importação de origem; NULL para lançamento manual.';
COMMENT ON COLUMN transactions.external_id IS 'Identificador do banco (FITID do OFX), quando houver.';
COMMENT ON COLUMN transactions.description IS 'Descrição com espaços repetidos colapsados.';
COMMENT ON COLUMN transactions.original_description IS 'Descrição como veio do banco (aparada).';
COMMENT ON COLUMN transactions.amount IS 'Valor absoluto (> 0); o sentido está em transaction_type.';
COMMENT ON COLUMN transactions.transaction_type IS 'DEBIT ou CREDIT. No cartão, compra é DEBIT e pagamento/estorno é CREDIT.';
COMMENT ON COLUMN transactions.kind IS 'REGULAR ou CARD_PAYMENT (pagamento de fatura, decisão D8).';
COMMENT ON COLUMN transactions.classification_source IS 'AI, PATTERN, USER ou NONE.';
COMMENT ON COLUMN transactions.classification_confidence IS 'Confiança da classificação, de 0 a 100.';
COMMENT ON COLUMN transactions.review_status IS 'PENDING até a importação ser concluída; depois CONFIRMED.';
COMMENT ON COLUMN transactions.affects_balance IS 'Se o lançamento alterou o saldo da conta (decisão D3).';
COMMENT ON COLUMN transactions.pix_details IS 'Detalhes do Pix (Story 5.6).';
COMMENT ON COLUMN transactions.installment_group_id IS 'Mesmo valor em todas as parcelas de uma compra (decisão D7).';
COMMENT ON COLUMN transactions.is_future_installment IS 'Parcela futura criada a partir de uma parcela importada (Story 5.7).';
