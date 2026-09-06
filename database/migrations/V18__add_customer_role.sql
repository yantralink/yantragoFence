-- V18__add_customer_role.sql
-- Add 'customer' role for end users (farmers) who use the mobile app.
-- They can view their own machine, GPS, telemetry, and send fencing on/off commands.

INSERT INTO roles (id, organization_id, name, description, is_system_role, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000014',
    NULL,
    'customer',
    'End user / farmer. Mobile app access. View own machine, GPS, telemetry, fencing on/off.',
    TRUE,
    now(),
    now()
) ON CONFLICT DO NOTHING;

-- Grant customer role permissions: read machine, send fencing commands, read telemetry/location/alerts
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'customer'
  AND p.name IN (
    'machine:read',
    'command:read',
    'command:write',
    'telemetry:read',
    'location:read',
    'alert:read',
    'notification:read',
    'settings:read'
  )
ON CONFLICT DO NOTHING;
