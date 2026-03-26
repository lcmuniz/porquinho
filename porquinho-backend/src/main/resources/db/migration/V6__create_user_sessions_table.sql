-- V6__create_user_sessions_table.sql
-- Story 1.5: Tabela para rastreamento de sessoes ativas

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    jwt_token_hash VARCHAR(64) NOT NULL,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_activity TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_jwt_token_hash ON user_sessions(jwt_token_hash);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at) WHERE revoked_at IS NULL;
