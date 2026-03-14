-- V1__init.sql
-- Baseline migration for porquinho database
-- Database: PostgreSQL 16+ via Supabase
-- Project: porquinho - Personal Finance Manager
--
-- This baseline establishes the Flyway migration history.
-- Future migrations will create business tables (users, transactions, etc.)
-- following the naming convention: V{N}__{description}.sql
--
-- Naming conventions for all future migrations:
--   Tables:      snake_case plural (users, transactions, budget_categories)
--   Columns:     snake_case (user_id, created_at, transaction_date)
--   Primary Keys: id UUID PRIMARY KEY DEFAULT gen_random_uuid()
--   Indexes:     idx_{table}_{column} (idx_transactions_user_id)
--   Constraints: {table}_{column}_{type} (users_email_unique)

-- Enable pgcrypto extension (if not already enabled by Supabase)
-- Note: gen_random_uuid() is built-in for PostgreSQL 13+ -- no extension needed
SELECT 1; -- no-op statement to produce a valid migration
