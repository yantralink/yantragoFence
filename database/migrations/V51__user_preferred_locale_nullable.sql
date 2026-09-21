-- V51: Make users.preferred_locale nullable (unset) and constrain its values.
--
-- Per Multilingual Plan Phase 4: NULL means "user has not chosen a language";
-- the effective notification fallback is English. The previous behavior
-- (V39: NOT NULL DEFAULT 'en') made "unset" unrepresentable and silently
-- backfilled existing users as explicit 'en'.
--
-- Also adds a CHECK constraint so invalid codes are rejected at the
-- database level (defense in depth behind Bean Validation).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Per AGENTS.md rule 14: no destructive DDL — this only relaxes constraints
-- (DROP DEFAULT / DROP NOT NULL) and adds a constraint; no data is dropped
-- or rewritten. Existing 'en' values are left as-is (a user recorded as 'en'
-- behaves identically to unset for notifications).

ALTER TABLE users ALTER COLUMN preferred_locale DROP DEFAULT;
ALTER TABLE users ALTER COLUMN preferred_locale DROP NOT NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_preferred_locale;
ALTER TABLE users ADD CONSTRAINT chk_users_preferred_locale
    CHECK (preferred_locale IS NULL OR preferred_locale IN ('en', 'hi', 'mr'));

COMMENT ON COLUMN users.preferred_locale IS 'User preferred notification language: en, hi, mr, or NULL (unset — falls back to en)';
