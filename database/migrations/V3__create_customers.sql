-- V3__create_customers.sql
-- End users (customers) within a tenant. Machines are assigned to customers.

CREATE TABLE IF NOT EXISTS customers (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    phone             VARCHAR(50),
    address           TEXT,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE customers IS 'End users within a tenant. Machines are assigned to customers.';
