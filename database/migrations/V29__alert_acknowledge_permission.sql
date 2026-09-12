-- V29__alert_acknowledge_permission.sql
-- Phase 1 fix: seed the alert:acknowledge permission and grant it to
-- customer, admin, org_admin, and super_admin roles.
--
-- Previously AlertController required alert:acknowledge but no migration
-- created it, so only super_admin could acknowledge alerts.

INSERT INTO permissions (name, description) VALUES
('alert:acknowledge', 'Acknowledge alerts for assigned machines')
ON CONFLICT (name) DO NOTHING;

-- Grant to customer role (users acknowledge their own assigned-machine alerts)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'customer'
  AND p.name = 'alert:acknowledge'
ON CONFLICT DO NOTHING;

-- Grant to admin roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('admin', 'org_admin', 'super_admin')
  AND p.name = 'alert:acknowledge'
ON CONFLICT DO NOTHING;
