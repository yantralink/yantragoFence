-- V2__seed_sample_organization.sql
-- Seed a sample tenant organization for dev/staging with an org_admin user.

BEGIN;

-- ===== Sample organization =====
INSERT INTO organizations (id, name, slug, white_label_config, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'YantraGO Demo Tenant',
    'yantrago-demo',
    '{"primaryColor": "#2563eb", "logoUrl": null}'::JSONB,
    TRUE
)
ON CONFLICT DO NOTHING;

-- ===== Org admin user =====
-- BCrypt hash for "password" — CHANGE IMMEDIATELY after first login.
INSERT INTO users (id, organization_id, email, password_hash, full_name, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000001',
    'admin@yantrago-demo.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Demo Org Admin',
    TRUE
)
ON CONFLICT DO NOTHING;

-- Assign org_admin role (system role, NULL org) to the demo org admin.
INSERT INTO user_roles (user_id, role_id)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000011'
)
ON CONFLICT DO NOTHING;

COMMIT;
