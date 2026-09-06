-- V4__create_machines_and_devices.sql
-- machines: YantraGO fencing machines owned by a tenant, assigned to customers.
-- devices: physical tracker devices (IMEI, SIM, protocol) bound to a machine.
-- machine_assignments: machine -> customer mapping (history-aware).

CREATE TABLE IF NOT EXISTS machines (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_id       UUID REFERENCES customers(id) ON DELETE SET NULL,
    name              VARCHAR(255) NOT NULL,
    serial_number     VARCHAR(100),
    model             VARCHAR(100),
    status            VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | FAULTY | RETIRED
    is_online         BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, serial_number)
);

COMMENT ON TABLE machines IS 'YantraGO fencing machines owned by a tenant.';
COMMENT ON COLUMN machines.status IS 'ACTIVE | INACTIVE | FAULTY | RETIRED';

CREATE TABLE IF NOT EXISTS devices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id        UUID REFERENCES machines(id) ON DELETE SET NULL,
    imei              VARCHAR(20) NOT NULL UNIQUE,
    sim_number        VARCHAR(30),
    protocol_type     VARCHAR(20) NOT NULL, -- CONCOX_V5 | JT808 | FENCING
    firmware_version  VARCHAR(50),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE devices IS 'Physical tracker devices (IMEI, SIM, protocol) bound to a machine.';
COMMENT ON COLUMN devices.protocol_type IS 'CONCOX_V5 | JT808 | FENCING';

CREATE TABLE IF NOT EXISTS machine_assignments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id        UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    customer_id       UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    assigned_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE machine_assignments IS 'Machine to customer mapping (history-aware).';
