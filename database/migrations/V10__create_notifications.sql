-- V10__create_notifications.sql
-- notifications: notification records (one per dispatch attempt per channel).
-- notification_preferences: per-user preferences for which channels to use.

CREATE TABLE IF NOT EXISTS notifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id           UUID REFERENCES users(id) ON DELETE CASCADE,
    alert_id          UUID REFERENCES alerts(id) ON DELETE CASCADE,
    channel           VARCHAR(20) NOT NULL, -- PUSH | EMAIL | SMS | WHATSAPP
    title             VARCHAR(255),
    body              TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | DELIVERED | FAILED
    provider          VARCHAR(50),           -- FCM | EXPO | ONE_SIGNAL | SMTP | TWILIO | MSG91
    provider_message_id VARCHAR(255),
    error             TEXT,
    sent_at           TIMESTAMPTZ,
    delivered_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE notifications IS 'Notification records (one per dispatch attempt per channel).';
COMMENT ON COLUMN notifications.channel IS 'PUSH | EMAIL | SMS | WHATSAPP';
COMMENT ON COLUMN notifications.status IS 'PENDING | SENT | DELIVERED | FAILED';

CREATE TABLE IF NOT EXISTS notification_preferences (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    channel           VARCHAR(20) NOT NULL, -- PUSH | EMAIL | SMS | WHATSAPP
    alert_type        VARCHAR(50),          -- NULL = all alert types
    is_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, channel, alert_type)
);

COMMENT ON TABLE notification_preferences IS 'Per-user notification preferences per channel and alert type.';
