-- V1__seed_super_admin.sql
-- Seed the platform-level super_admin user and system roles/permissions.
-- IMPORTANT: Change the password immediately after first login.
-- Default password: "password" (BCrypt hash below).
-- Regenerate with: new BCryptPasswordEncoder().encode("your-new-password")

BEGIN;

-- ===== System roles =====
INSERT INTO roles (id, organization_id, name, description, is_system_role)
VALUES
    ('00000000-0000-0000-0000-000000000010', NULL, 'super_admin', 'Platform super administrator (full access)', TRUE),
    ('00000000-0000-0000-0000-000000000011', NULL, 'org_admin',   'Organization administrator (tenant-scoped full access)', TRUE),
    ('00000000-0000-0000-0000-000000000012', NULL, 'operator',    'Operator (read + command issuance, no user management)', TRUE),
    ('00000000-0000-0000-0000-000000000013', NULL, 'viewer',      'Viewer (read-only)', TRUE)
ON CONFLICT DO NOTHING;

-- ===== Permissions =====
INSERT INTO permissions (id, name, description)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'organization:read',   'Read organization details'),
    ('00000000-0000-0000-0000-000000000002', 'organization:write',  'Update organization details'),
    ('00000000-0000-0000-0000-000000000003', 'user:read',           'Read users'),
    ('00000000-0000-0000-0000-000000000004', 'user:write',          'Create/update users'),
    ('00000000-0000-0000-0000-000000000005', 'user:delete',         'Delete users'),
    ('00000000-0000-0000-0000-000000000006', 'customer:read',       'Read customers'),
    ('00000000-0000-0000-0000-000000000007', 'customer:write',      'Create/update customers'),
    ('00000000-0000-0000-0000-000000000008', 'customer:delete',     'Delete customers'),
    ('00000000-0000-0000-0000-000000000009', 'machine:read',        'Read machines'),
    ('00000000-0000-0000-0000-00000000000a', 'machine:write',       'Create/update machines'),
    ('00000000-0000-0000-0000-00000000000b', 'machine:delete',      'Delete machines'),
    ('00000000-0000-0000-0000-00000000000c', 'device:read',         'Read devices'),
    ('00000000-0000-0000-0000-00000000000d', 'device:write',        'Create/update devices'),
    ('00000000-0000-0000-0000-00000000000e', 'command:read',        'Read command history'),
    ('00000000-0000-0000-0000-00000000000f', 'command:write',       'Issue machine commands (ON/OFF)'),
    ('00000000-0000-0000-0000-000000000020', 'telemetry:read',      'Read telemetry data'),
    ('00000000-0000-0000-0000-000000000021', 'location:read',       'Read location data'),
    ('00000000-0000-0000-0000-000000000022', 'alert:read',          'Read alerts'),
    ('00000000-0000-0000-0000-000000000023', 'alert:write',         'Configure/acknowledge alerts'),
    ('00000000-0000-0000-0000-000000000024', 'notification:read',   'Read notifications'),
    ('00000000-0000-0000-0000-000000000025', 'notification:write',  'Configure notification preferences'),
    ('00000000-0000-0000-0000-000000000026', 'settings:read',       'Read settings'),
    ('00000000-0000-0000-0000-000000000027', 'settings:write',      'Update settings'),
    ('00000000-0000-0000-0000-000000000028', 'report:read',         'Generate/export reports'),
    ('00000000-0000-0000-0000-000000000029', 'recharge:read',       'Read recharge records'),
    ('00000000-0000-0000-0000-00000000002a', 'recharge:write',      'Create recharge records'),
    ('00000000-0000-0000-0000-00000000002b', 'audit:read',          'Read audit logs')
ON CONFLICT DO NOTHING;

-- ===== super_admin gets ALL permissions =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'super_admin'
ON CONFLICT DO NOTHING;

-- ===== org_admin gets all except user:delete and audit:read (platform-only) =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'org_admin'
  AND p.name NOT IN ('user:delete', 'audit:read')
ON CONFLICT DO NOTHING;

-- ===== operator: read + command + telemetry + location + alert ack =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'operator'
  AND p.name IN (
    'organization:read', 'user:read', 'customer:read', 'customer:write',
    'machine:read', 'machine:write', 'device:read', 'device:write',
    'command:read', 'command:write', 'telemetry:read', 'location:read',
    'alert:read', 'alert:write', 'notification:read', 'settings:read',
    'report:read', 'recharge:read', 'recharge:write'
  )
ON CONFLICT DO NOTHING;

-- ===== viewer: read-only across the board =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'viewer'
  AND p.name LIKE '%:read'
ON CONFLICT DO NOTHING;

-- ===== super_admin user (platform-level, NULL organization_id) =====
-- BCrypt hash for "password" — CHANGE IMMEDIATELY after first login.
INSERT INTO users (id, organization_id, email, password_hash, full_name, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    NULL,
    'superadmin@yantrago.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Super Admin',
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000010'
)
ON CONFLICT DO NOTHING;

COMMIT;
