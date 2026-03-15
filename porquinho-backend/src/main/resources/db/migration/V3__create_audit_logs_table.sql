-- V3__create_audit_logs_table.sql
-- Create audit_logs table for security and compliance logging
-- Database: PostgreSQL 16+ via Supabase
-- Story: 1-1-user-registration-with-google-oauth

-- Audit logs table: Stores security events and user actions for compliance (NFR20)
CREATE TABLE audit_logs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    metadata TEXT,

    -- Foreign key to users table (nullable for system events)
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for efficient querying
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_user_timestamp ON audit_logs(user_id, timestamp DESC);

-- Comments for documentation
COMMENT ON TABLE audit_logs IS 'Audit log entries for security and compliance tracking (NFR20). Records user actions and system events.';
COMMENT ON COLUMN audit_logs.id IS 'Primary key: UUID generated automatically';
COMMENT ON COLUMN audit_logs.user_id IS 'Reference to user who performed the action (nullable for system events)';
COMMENT ON COLUMN audit_logs.event_type IS 'Type of event: user_registration, user_login, user_logout, etc.';
COMMENT ON COLUMN audit_logs.timestamp IS 'Timestamp when event occurred';
COMMENT ON COLUMN audit_logs.ip_address IS 'IP address of the client (IPv4 or IPv6)';
COMMENT ON COLUMN audit_logs.metadata IS 'Additional event metadata as JSON or text';
