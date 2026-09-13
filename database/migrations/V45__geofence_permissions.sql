-- V45: RBAC permissions for geofence CRUD endpoints (Phase 10).
--
-- GeofenceController uses @PreAuthorize with geofence:read, geofence:write,
-- geofence:delete. Without these permission seeds, only super_admin can access
-- the geofence endpoints. This migration grants geofence permissions to admin
-- and org_admin roles.
--
-- Per AGENTS.md rule 9: sensitive operations require authorization.
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== 1. Add geofence permissions =====
INSERT INTO permissions (name, description) VALUES
('geofence:read', 'View geo-fences for machines in organization'),
('geofence:write', 'Create and update geo-fences'),
('geofence:delete', 'Delete geo-fences')
ON CONFLICT (name) DO NOTHING;

-- ===== 2. Grant geofence permissions to admin and org_admin roles =====
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('admin', 'org_admin', 'super_admin')
  AND p.name IN (
    'geofence:read',
    'geofence:write',
    'geofence:delete'
  )
ON CONFLICT DO NOTHING;
