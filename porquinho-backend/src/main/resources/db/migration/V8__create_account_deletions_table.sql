-- V8: Create account_deletions table for LGPD right to erasure (Story 1.7)
-- Tracks deletion requests with 30-day retention before permanent purge.
-- Records persist after status=PURGED for audit/compliance.

CREATE TABLE account_deletions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PURGE'
        CHECK (status IN ('PENDING_PURGE', 'PURGED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_purge_at TIMESTAMPTZ NOT NULL,
    purged_at TIMESTAMPTZ NULL,
    requested_ip VARCHAR(45)
);

-- Index for lookups by user (idempotency check on request)
CREATE INDEX idx_account_deletions_user_id ON account_deletions(user_id);

-- Composite index for the daily purge job query
-- (status, scheduled_purge_at) WHERE status='PENDING_PURGE'
CREATE INDEX idx_account_deletions_status_scheduled
    ON account_deletions(status, scheduled_purge_at);

-- Defense-in-depth: prevent two concurrent requests from creating duplicate
-- PENDING_PURGE rows for the same user (race past the application-level
-- idempotency check). The partial filter allows historical PURGED rows to
-- coexist for the same user_id (e.g., if an admin ever recreates the same UUID).
CREATE UNIQUE INDEX uniq_pending_deletion_per_user
    ON account_deletions(user_id) WHERE status = 'PENDING_PURGE';

COMMENT ON TABLE account_deletions IS
    'Tracks LGPD account deletion requests. Records persist after purge for audit/compliance.';
COMMENT ON COLUMN account_deletions.user_id IS
    'Reference to deleted user (no FK - user record will be hard-deleted on purge). After status=PURGED, this user_id no longer resolves to a row in users.';
COMMENT ON COLUMN account_deletions.status IS
    'PENDING_PURGE: awaiting scheduled hard-delete. PURGED: hard-delete completed.';
COMMENT ON COLUMN account_deletions.scheduled_purge_at IS
    'Timestamp after which the daily purge job will hard-delete the user data.';
COMMENT ON COLUMN account_deletions.purged_at IS
    'Timestamp when hard-delete completed (NULL while pending).';
