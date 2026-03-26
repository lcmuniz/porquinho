-- V7: Create data_exports table for LGPD data portability
-- Stores metadata about user data export requests

CREATE TABLE data_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    file_path TEXT,
    download_token UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    downloaded_at TIMESTAMPTZ NULL
);

-- Index for looking up exports by user
CREATE INDEX idx_data_exports_user_id ON data_exports(user_id);

-- Index for download token lookup
CREATE INDEX idx_data_exports_download_token ON data_exports(download_token);

-- Index for cleanup job (finding expired exports)
CREATE INDEX idx_data_exports_expires_at ON data_exports(expires_at);
