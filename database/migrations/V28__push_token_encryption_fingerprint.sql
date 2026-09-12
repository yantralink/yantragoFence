-- V28__push_token_encryption_fingerprint.sql
-- Phase 5 fix: protected token storage — add fingerprint for uniqueness,
-- prepare for encrypted token storage.
--
-- Per notification plan Phase 5: "Encrypt retrievable token values at rest
-- and store a SHA-256 fingerprint for uniqueness instead of the raw token."
--
-- All changes are additive. The token column is widened to hold encrypted
-- values (which are longer than raw FCM tokens).

-- Ensure pgcrypto is available for digest() function
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Add token_fingerprint column (SHA-256 hex of the raw token)
ALTER TABLE user_device_tokens ADD COLUMN IF NOT EXISTS token_fingerprint VARCHAR(64);
COMMENT ON COLUMN user_device_tokens.token_fingerprint IS 'SHA-256 hex of the raw token, used for uniqueness lookups';

-- Backfill fingerprints for existing tokens (SHA-256 of the raw token value)
UPDATE user_device_tokens
SET token_fingerprint = encode(digest(token, 'sha256'), 'hex')
WHERE token_fingerprint IS NULL;

-- Make fingerprint NOT NULL after backfill
ALTER TABLE user_device_tokens ALTER COLUMN token_fingerprint SET NOT NULL;

-- Drop the old unique index on (user_id, token) and recreate on fingerprint
DROP INDEX IF EXISTS uq_device_tokens_user_token_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_device_tokens_user_token_fingerprint_active
    ON user_device_tokens (user_id, token_fingerprint) WHERE is_active = TRUE;

-- Add index for fingerprint lookups
CREATE INDEX IF NOT EXISTS idx_device_tokens_fingerprint
    ON user_device_tokens (token_fingerprint) WHERE is_active = TRUE;

-- Widen token column to accommodate encrypted values (AES-GCM adds ~28 bytes overhead)
ALTER TABLE user_device_tokens ALTER COLUMN token TYPE VARCHAR(1024);

-- Also widen the token snapshot in push_delivery_jobs
ALTER TABLE push_delivery_jobs ALTER COLUMN token TYPE VARCHAR(1024);
