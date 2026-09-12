-- V30__notification_inbox_unique_key.sql
-- SIG 8: Fix notification_inbox unique key to include organization_id.
-- SIG 12: Deduplicate notification_preferences before unique index creation (safety net).
-- All changes are additive or non-destructive (no DROP TABLE).

-- ===== SIG 12: Deduplicate notification_preferences before unique index creation =====
-- The V24 migration created a partial unique index uq_notif_prefs_user_channel_type
-- on (user_id, channel, COALESCE(alert_type, '')). If any duplicate rows existed
-- before that index was created, this dedup step removes them as a safety net.
-- Runs BEFORE any further unique index creation on notification_preferences.
DELETE FROM notification_preferences p1
USING notification_preferences p2
WHERE p1.id < p2.id
  AND p1.user_id = p2.user_id
  AND p1.channel = p2.channel
  AND COALESCE(p1.alert_type, '') = COALESCE(p2.alert_type, '');

-- ===== SIG 8: Fix notification_inbox unique key (add organization_id) =====
-- The original unique constraint was an inline UNIQUE (event_id, user_id) from V24.
-- PostgreSQL auto-names inline unique constraints as <table>_<col1>_<col2>_key.
-- Drop the old constraint and replace with one that includes organization_id,
-- so the same event_id can be delivered to users across organizations without
-- collision (event_id is globally unique in practice, but including org_id makes
-- the tenant boundary explicit and safe).

ALTER TABLE notification_inbox DROP CONSTRAINT IF EXISTS notification_inbox_event_id_user_id_key;

ALTER TABLE notification_inbox ADD CONSTRAINT uq_notif_inbox_org_user_event
    UNIQUE (organization_id, user_id, event_id);

-- Index on organization_id for tenant-scoped queries (if not already present).
CREATE INDEX IF NOT EXISTS idx_notif_inbox_organization_id
    ON notification_inbox (organization_id);
