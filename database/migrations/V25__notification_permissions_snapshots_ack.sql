-- V25__notification_permissions_snapshots_ack.sql
-- Phase 3 completion: Grant notification permissions to roles, add recipient
-- snapshots, and add acknowledgement support.

-- ===== 1. Grant notification permissions to customer role =====
-- V18 already grants notification:read, but we need mark_read and preference access.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'customer'
  AND p.name IN (
    'notification:mark_read',
    'notification_preference:read',
    'notification_preference:write'
  )
ON CONFLICT DO NOTHING;

-- ===== 2. Grant notification permissions to admin roles =====
-- Admins can read all notifications, write, delete, and manage preferences.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('admin', 'org_admin', 'super_admin')
  AND p.name IN (
    'notification:read',
    'notification:read_all',
    'notification:write',
    'notification:delete',
    'notification:mark_read',
    'notification_preference:read',
    'notification_preference:write'
  )
ON CONFLICT DO NOTHING;

-- ===== 2b. Add alert_rule permissions and grant to admin roles =====
-- Phase 2: AlertRuleController uses alert_rule:read/write/delete.
INSERT INTO permissions (name, description) VALUES
('alert_rule:read', 'Read alert rules in organization'),
('alert_rule:write', 'Create/update alert rules'),
('alert_rule:delete', 'Delete alert rules')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('admin', 'org_admin', 'super_admin')
  AND p.name IN (
    'alert_rule:read',
    'alert_rule:write',
    'alert_rule:delete'
  )
ON CONFLICT DO NOTHING;

-- ===== 3. Add recipient snapshot columns to notification_inbox =====
-- Event-time recipient snapshot: captures customer identity at event time.
-- If the customer is later deleted or renamed, the inbox item retains context.
ALTER TABLE notification_inbox
    ADD COLUMN IF NOT EXISTS recipient_customer_id UUID REFERENCES customers(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS recipient_customer_name VARCHAR(255);

COMMENT ON COLUMN notification_inbox.recipient_customer_id IS 'Event-time snapshot: customer ID at the time of notification creation.';
COMMENT ON COLUMN notification_inbox.recipient_customer_name IS 'Event-time snapshot: customer name at the time of notification creation.';

-- ===== 4. Add acknowledgement columns to notification_inbox =====
-- Authorized acknowledgement: the recipient confirms they have seen and
-- acknowledged the alert. This is separate from is_read (which is automatic
-- on open/tap). Acknowledgement is an explicit user action.
ALTER TABLE notification_inbox
    ADD COLUMN IF NOT EXISTS is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMPTZ;

COMMENT ON COLUMN notification_inbox.is_acknowledged IS 'TRUE if the recipient explicitly acknowledged this notification.';
COMMENT ON COLUMN notification_inbox.acknowledged_at IS 'Timestamp of explicit acknowledgement.';

CREATE INDEX IF NOT EXISTS idx_inbox_user_acknowledged
    ON notification_inbox (user_id) WHERE is_acknowledged = FALSE;
