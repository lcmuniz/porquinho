-- V41: session_id do JWT do Supabase em user_sessions (retro Epic 1 A2)
-- O claim session_id não muda quando o Supabase renova o access token: a revogação passa a valer para os tokens
-- renovados. NULL nas sessões gravadas antes desta migração, que seguem revogadas pelo hash do token.
ALTER TABLE user_sessions ADD COLUMN session_id UUID NULL;

-- O JwtAuthenticationConverter consulta session_id revogado a cada requisição: o índice cobre só as linhas revogadas
CREATE INDEX idx_user_sessions_revoked_session_id ON user_sessions(session_id) WHERE revoked_at IS NOT NULL;
