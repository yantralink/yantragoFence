-- V20__make_device_org_nullable.sql
-- Make devices.organization_id nullable so devices can be created for
-- unassigned inventory machines (before super admin assigns them to an org).

ALTER TABLE devices ALTER COLUMN organization_id DROP NOT NULL;

COMMENT ON COLUMN devices.organization_id IS 'Nullable for devices on unassigned inventory machines. Set when machine is assigned to an organization.';
