-- V39: Add preferred_locale column to users table.
--
-- Per Phase 8: stores the user's preferred notification language.
-- Supported values: 'en' (English), 'hi' (Hindi), 'mr' (Marathi).
-- Defaults to 'en' for existing users.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en';

COMMENT ON COLUMN users.preferred_locale IS 'User preferred notification language: en, hi, or mr';
