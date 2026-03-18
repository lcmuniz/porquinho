-- Add failed login tracking fields for account locking
-- Story 1.3: User Login with Any Configured Method

ALTER TABLE users
ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE users
ADD COLUMN locked_until TIMESTAMP NULL;

-- Create index for locked_until to optimize locking checks
-- Note: H2 doesn't support partial indexes (WHERE clause), so we create a full index
-- In production PostgreSQL, this could be optimized with: WHERE locked_until IS NOT NULL
CREATE INDEX idx_users_locked_until ON users(locked_until);
