-- V4__add_lgpd_consent_to_users.sql
-- Add LGPD consent tracking to users table
-- Database: PostgreSQL 16+ via Supabase
-- Story: 1-1-user-registration-with-google-oauth

-- Add lgpd_consent_at column to track when user consented to data processing (NFR25)
ALTER TABLE users ADD COLUMN lgpd_consent_at TIMESTAMP;

-- Create index for consent queries
CREATE INDEX idx_users_lgpd_consent_at ON users(lgpd_consent_at);

-- Comment for documentation
COMMENT ON COLUMN users.lgpd_consent_at IS 'LGPD consent timestamp - when user agreed to data processing terms (NFR25)';
