-- V16__fix_refresh_tokens_nullable_org.sql
-- Make organization_id nullable in refresh_tokens to support super_admin (platform-level, no organization).
-- Super admin users have NULL organization_id and should be able to get refresh tokens.

ALTER TABLE refresh_tokens ALTER COLUMN organization_id DROP NOT NULL;
