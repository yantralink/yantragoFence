-- V24__notification_inbox_delivery.sql
-- Phase 3: Recipient inbox, delivery attempts, deterministic preference uniqueness,
-- and template versioning. All changes are additive.

-- ===== 1. notification_inbox: one item per eligible recipient per alert transition =====
-- This is the user-facing inbox. Each row is a notification delivered to a specific user.
-- Deduplication is enforced via (event_id, user_id) unique constraint.

CREATE TABLE IF NOT EXISTS notification_inbox (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_id            UUID REFERENCES alerts(id) ON DELETE CASCADE,
    event_id            UUID NOT NULL,                       -- AlertTransitionMessage.eventId
    alert_type          VARCHAR(50) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    incident_state      VARCHAR(20) NOT NULL,                -- OPEN | RESOLVED | ESCALATED
    title               VARCHAR(255) NOT NULL,
    body                TEXT NOT NULL,
    machine_id          UUID,
    observed_value       DOUBLE PRECISION,
    observed_unit       VARCHAR(20),
    locale              VARCHAR(10) NOT NULL DEFAULT 'en',
    template_version    INTEGER NOT NULL DEFAULT 1,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)                              -- per-event/recipient dedup
);

COMMENT ON TABLE notification_inbox IS 'Per-recipient notification inbox items. One per (event, user).';

CREATE INDEX IF NOT EXISTS idx_inbox_user_org ON notification_inbox (organization_id, user_id);
CREATE INDEX IF NOT EXISTS idx_inbox_user_unread ON notification_inbox (user_id) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_inbox_user_created ON notification_inbox (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inbox_alert ON notification_inbox (alert_id);

-- ===== 2. notification_delivery: per-channel delivery attempts =====
-- Tracks each attempt to deliver an inbox item via a channel (PUSH, EMAIL, SMS).
-- An inbox item may have multiple delivery attempts (retries) across channels.

CREATE TABLE IF NOT EXISTS notification_delivery (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbox_id            UUID NOT NULL REFERENCES notification_inbox(id) ON DELETE CASCADE,
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel              VARCHAR(20) NOT NULL,              -- PUSH | EMAIL | SMS | WHATSAPP
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | DELIVERED | FAILED | SKIPPED
    provider            VARCHAR(50),
    provider_message_id VARCHAR(255),
    error               TEXT,
    attempted_at        TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE notification_delivery IS 'Per-channel delivery attempts for inbox items.';
COMMENT ON COLUMN notification_delivery.status IS 'PENDING | SENT | DELIVERED | FAILED | SKIPPED';

CREATE INDEX IF NOT EXISTS idx_delivery_inbox ON notification_delivery (inbox_id);
CREATE INDEX IF NOT EXISTS idx_delivery_user_status ON notification_delivery (user_id, status);

-- ===== 3. notification_templates: versioned templates per alert type and locale =====
-- Provides localized title/body templates with fallback text.

CREATE TABLE IF NOT EXISTS notification_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID REFERENCES organizations(id) ON DELETE CASCADE, -- NULL = system default
    alert_type          VARCHAR(50) NOT NULL,
    incident_state      VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN | RESOLVED | ESCALATED
    locale              VARCHAR(10) NOT NULL DEFAULT 'en',
    title_template      VARCHAR(255) NOT NULL,
    body_template       TEXT NOT NULL,
    template_version    INTEGER NOT NULL DEFAULT 1,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (alert_type, incident_state, locale, template_version)
);

COMMENT ON TABLE notification_templates IS 'Versioned notification templates per alert type, state, and locale.';

-- ===== 4. Seed default templates for all enabled event types =====
-- English defaults. Multilingual templates can be added later.

INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'LOW_BATTERY', 'OPEN', 'en', 'Low Battery Alert', 'Battery is low on machine {machineName}: {observedValue}%', 1),
(NULL, 'LOW_BATTERY', 'RESOLVED', 'en', 'Battery Recovered', 'Battery level recovered on machine {machineName}.', 1),
(NULL, 'VOLTAGE_DROP', 'OPEN', 'en', 'Voltage Drop Alert', 'Voltage dropped on machine {machineName}: {observedValue}V', 1),
(NULL, 'VOLTAGE_DROP', 'RESOLVED', 'en', 'Voltage Recovered', 'Voltage recovered on machine {machineName}.', 1),
(NULL, 'GSM_SIGNAL_LOW', 'OPEN', 'en', 'Low GSM Signal', 'GSM signal is weak on machine {machineName}: {observedValue}', 1),
(NULL, 'GSM_SIGNAL_LOW', 'RESOLVED', 'en', 'GSM Signal Recovered', 'GSM signal recovered on machine {machineName}.', 1),
(NULL, 'DEVICE_OFFLINE', 'OPEN', 'en', 'Device Offline', 'Device for machine {machineName} has been offline for {observedValue} minutes.', 1),
(NULL, 'DEVICE_OFFLINE', 'RESOLVED', 'en', 'Device Back Online', 'Device for machine {machineName} has reconnected.', 1),
(NULL, 'SIM_EXPIRY', 'OPEN', 'en', 'SIM Expiry Reminder', 'SIM plan for machine {machineName} expires in {observedValue} days.', 1),
(NULL, 'SIM_EXPIRY', 'RESOLVED', 'en', 'SIM Expiry Resolved', 'SIM plan for machine {machineName} has been renewed or expired.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;

-- ===== 5. Fix notification_preferences uniqueness =====
-- The original UNIQUE (user_id, channel, alert_type) has a problem: NULL alert_type
-- is treated as distinct in PostgreSQL, allowing multiple "all types" preferences.
-- Replace with a partial index that treats NULL as a single value.

-- Drop the old unique constraint (if it exists as a table constraint)
ALTER TABLE notification_preferences DROP CONSTRAINT IF EXISTS notification_preferences_user_id_channel_alert_type_key;

-- Add deterministic uniqueness: one preference per (user, channel, alert_type)
-- where NULL alert_type means "all alert types" (only one such row allowed per channel).
CREATE UNIQUE INDEX IF NOT EXISTS uq_notif_prefs_user_channel_type
    ON notification_preferences (user_id, channel, COALESCE(alert_type, ''));

-- ===== 6. Add notification permissions to the permissions table =====
INSERT INTO permissions (name, description) VALUES
('notification:read', 'Read own notifications'),
('notification:read_all', 'Read all notifications in organization'),
('notification:write', 'Create/update notifications'),
('notification:delete', 'Delete notifications'),
('notification_preference:read', 'Read own notification preferences'),
('notification_preference:write', 'Update own notification preferences'),
('notification:mark_read', 'Mark notifications as read')
ON CONFLICT (name) DO NOTHING;

-- ===== 7. Backfill: ensure existing customers have user_id linked =====
-- (No-op if already done by V19. This is a safety net.)
-- Not needed here — V19 handles the link. We just ensure the index exists.
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON customers(user_id) WHERE user_id IS NOT NULL;
