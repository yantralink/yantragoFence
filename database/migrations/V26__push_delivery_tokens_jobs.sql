-- V26__push_delivery_tokens_jobs.sql
-- Phase 5: FCM Delivery and Mobile Push
-- Adds user device token storage, push delivery jobs, and push permissions.
-- All changes are additive.

-- ===== 1. user_device_tokens: FCM/APNs installation tokens per user =====
-- One user may have multiple device tokens (multi-device support).
-- Tokens are registered by the mobile app and removed on logout or when
-- the provider reports them invalid.

CREATE TABLE IF NOT EXISTS user_device_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token               VARCHAR(512) NOT NULL,          -- FCM registration token
    platform            VARCHAR(20) NOT NULL DEFAULT 'ANDROID', -- ANDROID | IOS | WEB
    device_label        VARCHAR(255),                   -- optional user-friendly device name
    app_version         VARCHAR(50),                    -- mobile app version at registration time
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at        TIMESTAMPTZ,                    -- last time a push was sent to this token
    invalidated_at      TIMESTAMPTZ,                    -- set when provider reports token invalid
    invalidation_reason VARCHAR(255),                  -- e.g. "UNREGISTERED", "INVALID_REGISTRATION"
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE user_device_tokens IS 'FCM/APNs push tokens registered by mobile clients. Multi-device supported.';

-- One active token per (user, token) — prevents duplicate registrations for the same token
CREATE UNIQUE INDEX IF NOT EXISTS uq_device_tokens_user_token_active
    ON user_device_tokens (user_id, token) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_active ON user_device_tokens (user_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_device_tokens_org ON user_device_tokens (organization_id);

-- ===== 2. push_delivery_jobs: queued push delivery attempts =====
-- Each row represents a push that needs to be sent (or retried) to a specific
-- device token for a specific inbox item. The scheduler claims rows using
-- SKIP LOCKED + lease for multi-instance safety.

CREATE TABLE IF NOT EXISTS push_delivery_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    inbox_id            UUID NOT NULL REFERENCES notification_inbox(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token_id     UUID NOT NULL REFERENCES user_device_tokens(id) ON DELETE CASCADE,
    token               VARCHAR(512) NOT NULL,           -- snapshot of token at enqueue time
    title               VARCHAR(255) NOT NULL,
    body                TEXT NOT NULL,
    data_payload        JSONB,                          -- additional data (inbox_id, alert_type, etc.)
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | FAILED | CANCELLED | EXPIRED
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 3,
    provider            VARCHAR(50),                    -- FCM | APNS | NONE
    provider_message_id VARCHAR(255),                   -- FCM message ID (ACCEPTED_BY_PROVIDER)
    error               TEXT,
    ttl_seconds         INTEGER NOT NULL DEFAULT 86400, -- time-to-live for stale-event cancellation
    expires_at          TIMESTAMPTZ NOT NULL,            -- stale-event cancellation cutoff
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at     TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    claim_lease_until   TIMESTAMPTZ,                     -- worker lease for multi-instance safety
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE push_delivery_jobs IS 'Queued push delivery attempts with bounded retries and stale-event cancellation.';
COMMENT ON COLUMN push_delivery_jobs.status IS 'PENDING | SENT | FAILED | CANCELLED | EXPIRED';
COMMENT ON COLUMN push_delivery_jobs.provider_message_id IS 'FCM response ID. ACCEPTED_BY_PROVIDER, not device delivery.';

CREATE INDEX IF NOT EXISTS idx_push_jobs_status_next ON push_delivery_jobs (status, next_attempt_at)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_push_jobs_claim ON push_delivery_jobs (status, claim_lease_until)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_push_jobs_inbox ON push_delivery_jobs (inbox_id);
CREATE INDEX IF NOT EXISTS idx_push_jobs_user ON push_delivery_jobs (user_id, status);

-- ===== 3. Push permissions =====
INSERT INTO permissions (name, description) VALUES
('push_token:read', 'Read own device push tokens'),
('push_token:write', 'Register/unregister own device push tokens')
ON CONFLICT (name) DO NOTHING;

-- Grant push token permissions to customer role (users manage their own tokens)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'customer'
  AND p.name IN (
    'push_token:read',
    'push_token:write'
  )
ON CONFLICT DO NOTHING;

-- Grant push token permissions to admin roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('admin', 'org_admin', 'super_admin')
  AND p.name IN (
    'push_token:read',
    'push_token:write'
  )
ON CONFLICT DO NOTHING;

-- ===== 4. Add push_channel column to notification_preferences =====
-- Allows users to opt in/out of push channel per alert type
ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN notification_preferences.push_enabled IS 'Whether push delivery is enabled for this preference.';
