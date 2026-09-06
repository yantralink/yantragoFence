-- V1__create_organizations.sql
-- Tenants (companies using the YantraGO platform).
-- This is the root of multi-tenancy: every tenant-scoped table references organizations.id.

CREATE TABLE IF NOT EXISTS organizations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(255) NOT NULL,
    slug                 VARCHAR(100) NOT NULL UNIQUE,
    white_label_config   JSONB,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE organizations IS 'Tenants (companies using the YantraGO platform). Root of multi-tenancy.';
COMMENT ON COLUMN organizations.slug IS 'URL-safe unique identifier for the tenant.';
COMMENT ON COLUMN organizations.white_label_config IS 'Branding/theme config for white-label deployments (JSON).';
