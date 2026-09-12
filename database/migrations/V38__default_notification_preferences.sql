-- V38: Backfill default notification preferences for existing users.
--
-- Per Phase 7: new users get default preferences (IN_APP + PUSH enabled for
-- all alert types) when created via UserService.createUser. This migration
-- backfills the same defaults for users who already exist but have no
-- preferences configured.
--
-- Default policy:
--   - IN_APP channel: enabled for ALL alert types (NULL alert_type)
--   - PUSH channel: enabled for ALL alert types (NULL alert_type)
--
-- Note: super_admin users (null organization_id) are skipped — they don't
-- belong to a tenant and don't receive tenant-scoped notifications.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'IN_APP', NULL, true, true
FROM users u
WHERE u.organization_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM notification_preferences p
    WHERE p.user_id = u.id
      AND p.channel = 'IN_APP'
      AND p.alert_type IS NULL
)
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'PUSH', NULL, true, true
FROM users u
WHERE u.organization_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM notification_preferences p
    WHERE p.user_id = u.id
      AND p.channel = 'PUSH'
      AND p.alert_type IS NULL
)
ON CONFLICT DO NOTHING;
