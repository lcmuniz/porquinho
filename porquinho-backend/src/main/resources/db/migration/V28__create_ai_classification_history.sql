-- V28: ai_classification_history e progresso da classificação em transaction_imports (Story 5.8)
-- Uma linha por transação que a IA respondeu. predicted_category_id nulo: a IA respondeu que nenhuma categoria serve.
-- FKs sem ON DELETE (como V25/V26): o purge LGPD apaga esta tabela antes de transactions e de categories.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE ai_classification_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    predicted_category_id UUID REFERENCES categories(id),
    confidence INTEGER NOT NULL,
    was_corrected BOOLEAN NOT NULL DEFAULT FALSE,
    corrected_category_id UUID REFERENCES categories(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ai_classification_history_confidence_check CHECK (confidence >= 0 AND confidence <= 100)
);

CREATE INDEX idx_ai_classification_history_user_id ON ai_classification_history(user_id);
CREATE UNIQUE INDEX idx_ai_classification_history_transaction_id ON ai_classification_history(transaction_id);

COMMENT ON TABLE ai_classification_history IS 'Respostas da IA por transação (Story 5.8).';
COMMENT ON COLUMN ai_classification_history.predicted_category_id IS 'Categoria sugerida; NULL quando a IA não escolheu nenhuma.';
COMMENT ON COLUMN ai_classification_history.confidence IS 'Confiança devolvida pela IA, de 0 a 100.';
COMMENT ON COLUMN ai_classification_history.was_corrected IS 'Se o usuário trocou a categoria sugerida.';
COMMENT ON COLUMN ai_classification_history.corrected_category_id IS 'Categoria escolhida pelo usuário na correção.';

ALTER TABLE transaction_imports ADD COLUMN classification_total INTEGER NOT NULL DEFAULT 0;
ALTER TABLE transaction_imports ADD COLUMN classified_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE transaction_imports ADD CONSTRAINT transaction_imports_classification_progress_check
    CHECK (classification_total >= 0 AND classified_count >= 0 AND classified_count <= classification_total);

COMMENT ON COLUMN transaction_imports.classification_total IS 'Linhas enviadas à IA (o Y de "X de Y", Story 5.8).';
COMMENT ON COLUMN transaction_imports.classified_count IS 'Linhas de lotes concluídos, com sucesso ou não (o X de "X de Y", Story 5.8).';
