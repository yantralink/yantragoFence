-- V12__create_recharges.sql
-- SIM recharge records for devices (track data plan renewals).

CREATE TABLE IF NOT EXISTS recharges (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_id         UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    amount            NUMERIC(10,2) NOT NULL,
    currency          VARCHAR(3) NOT NULL DEFAULT 'INR',
    provider          VARCHAR(50),           -- e.g. JIO, AIRTEL, VI, BSNL
    plan_name         VARCHAR(100),
    recharged_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_until       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE recharges IS 'SIM recharge records for devices (data plan renewals).';
