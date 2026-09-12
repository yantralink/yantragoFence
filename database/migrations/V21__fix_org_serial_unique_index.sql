-- Fix: empty-string serial_number was treated as a non-NULL value by the
-- unique index idx_machines_org_serial, preventing two machines with empty
-- serial numbers from being assigned to the same organization.
--
-- 1. Convert existing empty-string serial_number values to NULL.
-- 2. Recreate the unique index to exclude empty strings.

-- Step 1: Normalize empty strings to NULL
UPDATE machines SET serial_number = NULL WHERE serial_number = '';

-- Step 2: Drop the old index and recreate with an empty-string exclusion
DROP INDEX IF EXISTS idx_machines_org_serial;

CREATE UNIQUE INDEX IF NOT EXISTS idx_machines_org_serial
    ON machines(organization_id, serial_number)
    WHERE organization_id IS NOT NULL
      AND serial_number IS NOT NULL
      AND serial_number <> '';
