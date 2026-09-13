-- V46: Grant geofence and alert_rule permissions to customer role (Phase 11).
--
-- Customers can now self-manage geofences and alert rules for their own
-- machines. This enables customer self-service theft protection without
-- requiring admin to configure each machine individually.
--
-- Per AGENTS.md rule 9: sensitive operations require authorization.
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
--
-- Customer permissions:
--   geofence:read   — view geofences for their own machines
--   geofence:write  — create/update geofences for their own machines
--   geofence:delete — delete geofences for their own machines
--   alert_rule:read  — view alert rules for their own machines
--   alert_rule:write — create/update alert rules for their own machines
--
-- Note: alert_rule:delete is NOT granted to customers. Customers can
-- deactivate rules but not delete them, preserving audit history.
-- Customer-level isolation is enforced in the service layer (GeofenceService,
-- AlertRuleService) by filtering on machines.customer_id.

-- ===== 1. Grant geofence permissions to customer role =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'customer'
  AND p.name IN (
    'geofence:read',
    'geofence:write',
    'geofence:delete'
  )
ON CONFLICT DO NOTHING;

-- ===== 2. Grant alert_rule:read and alert_rule:write to customer role =====
-- (alert_rule:delete is intentionally NOT granted to customers)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'customer'
  AND p.name IN (
    'alert_rule:read',
    'alert_rule:write'
  )
ON CONFLICT DO NOTHING;
