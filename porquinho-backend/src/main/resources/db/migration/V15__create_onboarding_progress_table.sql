-- V15: Create onboarding_progress table (Story 3.1 - boas-vindas e tutorial do orçamento por envelope)
-- Uma linha por usuário (UNIQUE user_id), criada na primeira leitura de GET /api/v1/onboarding/me.
-- steps_completed: array JSON em TEXT (mesmo padrão de audit_logs.metadata), ex.: ["tutorial_viewed"].
-- Sem índice parcial (H2 não suporta WHERE em índice); UNIQUE(user_id) já cria o índice da FK.
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE onboarding_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    current_step INT NOT NULL DEFAULT 1,
    steps_completed TEXT NOT NULL DEFAULT '[]',
    is_completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT onboarding_progress_user_id_unique UNIQUE (user_id)
);

COMMENT ON TABLE onboarding_progress IS 'Progresso do onboarding guiado de 8 passos (Epic 3). Uma linha por usuário.';
COMMENT ON COLUMN onboarding_progress.current_step IS 'Passo atual (1 a 8). O passo 1 é o tutorial do orçamento por envelope.';
COMMENT ON COLUMN onboarding_progress.steps_completed IS 'Array JSON com os passos concluídos, ex.: ["tutorial_viewed"] ou ["tutorial_skipped"].';
COMMENT ON COLUMN onboarding_progress.is_completed IS 'true quando os 8 passos foram concluídos (Story 3.8).';
