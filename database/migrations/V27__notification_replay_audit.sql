-- V27__notification_replay_audit.sql
-- Phase 6: DLQ replay audit trail.
-- Replays are privileged, audited, bounded, and idempotent.
-- This table records every DLQ replay attempt for operational accountability.

CREATE TABLE IF NOT EXISTS notification_replay_audit (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    replayed_by         UUID NOT NULL REFERENCES users(id),
    message_body        TEXT NOT NULL,                  -- original DLQ message content
    replay_reason       VARCHAR(500) NOT NULL,          -- why the replay was triggered
    replay_status       VARCHAR(20) NOT NULL,            -- SUCCESS | FAILED | REJECTED_STALE | REJECTED_NO_ACCESS
    error_detail        TEXT,
    event_id            UUID,                           -- extracted from message if available
    alert_type          VARCHAR(50),                    -- extracted from message if available
    machine_id          UUID,                           -- extracted from message if available
    organization_id     UUID,                           -- extracted from message if available
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE notification_replay_audit IS 'Audit trail for DLQ message replays. Privileged, bounded, idempotent.';

CREATE INDEX IF NOT EXISTS idx_replay_audit_user ON notification_replay_audit (replayed_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_replay_audit_status ON notification_replay_audit (replay_status);
CREATE INDEX IF NOT EXISTS idx_replay_audit_event ON notification_replay_audit (event_id) WHERE event_id IS NOT NULL;

-- ===== Phase 6: Add notification:replay permission =====
INSERT INTO permissions (name, description) VALUES
('notification:replay', 'Replay dead-lettered notification messages')
ON CONFLICT (name) DO NOTHING;

-- Grant replay permission to admin roles only (privileged operation)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('admin', 'org_admin', 'super_admin')
  AND p.name = 'notification:replay'
ON CONFLICT DO NOTHING;
