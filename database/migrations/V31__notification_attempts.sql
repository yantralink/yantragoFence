-- V31__notification_attempts.sql
-- SIG 9: Add notification_attempts table for per-channel delivery attempt tracking.
-- This is the canonical delivery-attempt log (replaces ad-hoc writes to notification_delivery).
-- All changes are additive.

CREATE TABLE IF NOT EXISTS notification_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    inbox_id        UUID NOT NULL REFERENCES notification_inbox(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel         VARCHAR(20) NOT NULL,  -- PUSH | EMAIL | SMS | IN_APP
    attempt_number  INTEGER NOT NULL DEFAULT 1,
    provider        VARCHAR(50),            -- FCM | APNS | SMTP | NONE
    provider_message_id VARCHAR(255),       -- provider response ID
    status          VARCHAR(20) NOT NULL,   -- PENDING | ACCEPTED_BY_PROVIDER | FAILED | SKIPPED
    error_code      VARCHAR(100),
    error_message   TEXT,
    attempted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE notification_attempts IS 'Per-channel delivery attempts for inbox items (canonical attempt log).';
COMMENT ON COLUMN notification_attempts.channel IS 'PUSH | EMAIL | SMS | IN_APP';
COMMENT ON COLUMN notification_attempts.status IS 'PENDING | ACCEPTED_BY_PROVIDER | FAILED | SKIPPED';

CREATE INDEX IF NOT EXISTS idx_notif_attempts_inbox ON notification_attempts(inbox_id);
CREATE INDEX IF NOT EXISTS idx_notif_attempts_user ON notification_attempts(user_id, attempted_at);
