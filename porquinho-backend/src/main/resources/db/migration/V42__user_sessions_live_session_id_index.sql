-- V42: índice de session_id nas sessões vivas (retro Epic 1 A3)
-- O JwtAuthenticationConverter grava a última atividade por session_id a cada requisição (com throttle de 5 min)
-- e checa expires_at da sessão; o índice da V41 cobre só as linhas revogadas.
CREATE INDEX idx_user_sessions_live_session_id ON user_sessions(session_id) WHERE revoked_at IS NULL;
