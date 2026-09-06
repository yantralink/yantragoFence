-- V14__create_refresh_tokens.sql
-- refresh_tokens: JWT refresh tokens (rotatable, one-time-use pattern).
-- login_sessions: active session tracking (one per login, supports device fingerprinting).

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    token_hash        VARCHAR(255) NOT NULL UNIQUE, -- SHA-256 hash of the refresh token
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    replaced_by       UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    ip_address        VARCHAR(45),
    user_agent        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens (rotatable, one-time-use). token_hash is SHA-256 of the raw token.';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Non-NULL when token has been revoked (rotation or logout).';
COMMENT ON COLUMN refresh_tokens.replaced_by IS 'Pointer to the new token that replaced this one (rotation chain).';

CREATE TABLE IF NOT EXISTS login_sessions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    session_token_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address        VARCHAR(45),
    user_agent        TEXT,
    device_fingerprint VARCHAR(255),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ
);

COMMENT ON TABLE login_sessions IS 'Active session tracking (one per login, supports device fingerprinting).';
