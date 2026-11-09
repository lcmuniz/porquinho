-- V30: user_classification_patterns (Story 5.11)
-- Um padrão por descrição normalizada e por usuário: a categoria que o usuário escolheu para ela na revisão.
-- As linhas que casam com um padrão são classificadas sem chamar a IA (origem PATTERN).
-- FKs sem ON DELETE (como V28): o purge LGPD apaga esta tabela antes de categories e de users.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE user_classification_patterns (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    description_pattern VARCHAR(500) NOT NULL,
    preferred_category_id UUID NOT NULL REFERENCES categories(id),
    usage_count INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT user_classification_patterns_usage_count_check CHECK (usage_count >= 1)
);

CREATE UNIQUE INDEX idx_user_classification_patterns_user_pattern
    ON user_classification_patterns(user_id, description_pattern);
CREATE INDEX idx_user_classification_patterns_category ON user_classification_patterns(preferred_category_id);

COMMENT ON TABLE user_classification_patterns IS 'Categoria escolhida pelo usuário por descrição normalizada (Story 5.11).';
COMMENT ON COLUMN user_classification_patterns.description_pattern IS 'Descrição sem marcador de parcela, minúscula, sem acentos, só letras separadas por um espaço.';
COMMENT ON COLUMN user_classification_patterns.preferred_category_id IS 'Última categoria salva pelo usuário para o padrão.';
COMMENT ON COLUMN user_classification_patterns.usage_count IS 'Vezes seguidas que o usuário salvou essa categoria para o padrão; volta a 1 quando ela muda.';
COMMENT ON COLUMN user_classification_patterns.last_used_at IS 'Última vez que o padrão foi aprendido ou aplicado numa importação.';
