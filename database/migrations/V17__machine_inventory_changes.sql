-- V17__machine_inventory_changes.sql
-- Machine inventory: add human-readable machine_id, make organization_id nullable
-- for unassigned inventory, add IN_STOCK status for machines without org/customer.

-- Make organization_id nullable (super admin creates machines as unassigned inventory)
ALTER TABLE machines ALTER COLUMN organization_id DROP NOT NULL;

-- Add human-readable machine_id column (e.g. YG000001)
ALTER TABLE machines ADD COLUMN IF NOT EXISTS machine_id VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS idx_machines_machine_id ON machines(machine_id) WHERE machine_id IS NOT NULL;

-- Backfill existing machines with generated IDs
DO $$
DECLARE
    r RECORD;
    seq INTEGER := 1;
BEGIN
    FOR r IN SELECT id FROM machines WHERE machine_id IS NULL ORDER BY created_at LOOP
        UPDATE machines SET machine_id = 'YG' || lpad(seq::text, 6, '0') WHERE id = r.id;
        seq := seq + 1;
    END LOOP;
END $$;

-- Make machine_id NOT NULL after backfill
ALTER TABLE machines ALTER COLUMN machine_id SET NOT NULL;

-- Update status: machines with no customer are IN_STOCK
UPDATE machines SET status = 'IN_STOCK' WHERE customer_id IS NULL AND status = 'ACTIVE';

-- Drop the old unique constraint on (organization_id, serial_number) since org can now be NULL
ALTER TABLE machines DROP CONSTRAINT IF EXISTS machines_organization_id_serial_number_key;

-- Add a new unique constraint that handles NULL organization_id
CREATE UNIQUE INDEX IF NOT EXISTS idx_machines_org_serial ON machines(organization_id, serial_number)
    WHERE organization_id IS NOT NULL AND serial_number IS NOT NULL;

COMMENT ON COLUMN machines.machine_id IS 'Human-readable unique ID. Auto-generated as YG + 6-digit sequence. Not editable after creation.';
