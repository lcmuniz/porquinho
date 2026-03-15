-- V2__create_users_table.sql
-- Create users table for authentication and user management
-- Database: PostgreSQL 16+ via Supabase
-- Story: 1-1-user-registration-with-google-oauth

-- Users table: Stores user account information and authentication details
CREATE TABLE users (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL CHECK (UPPER(auth_provider) IN ('GOOGLE', 'EMAIL')),
    google_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    -- Constraints
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_google_id_check CHECK (
        (UPPER(auth_provider) = 'GOOGLE' AND google_id IS NOT NULL) OR
        (UPPER(auth_provider) = 'EMAIL' AND google_id IS NULL)
    )
);

-- Indexes for efficient queries
-- Note: H2 doesn't support partial indexes (WHERE clause), so we create simple indexes
-- In production PostgreSQL, consider adding WHERE deleted_at IS NULL for better performance
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_google_id ON users(google_id);
CREATE INDEX idx_users_created_at ON users(created_at);

-- Comments for documentation
COMMENT ON TABLE users IS 'Stores user account information and authentication details. Supports soft delete via deleted_at.';
COMMENT ON COLUMN users.id IS 'Primary key: UUID generated automatically';
COMMENT ON COLUMN users.email IS 'User email address (unique, required)';
COMMENT ON COLUMN users.auth_provider IS 'Authentication method: google or email';
COMMENT ON COLUMN users.google_id IS 'Google OAuth user ID (required when auth_provider=google)';
COMMENT ON COLUMN users.created_at IS 'Timestamp when user was created';
COMMENT ON COLUMN users.updated_at IS 'Timestamp when user was last updated';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete timestamp (NULL for active users)';
COMMENT ON COLUMN users.deleted_by IS 'User ID who performed the deletion';
