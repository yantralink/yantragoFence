-- V13__create_settings.sql
-- machine_settings: per-machine configurable thresholds (overrides system defaults).
-- system_settings: platform-level configuration (key-value, org-scoped or global).

CREATE TABLE IF NOT EXISTS machine_settings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id        UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    setting_key       VARCHAR(100) NOT NULL,
    setting_value     TEXT NOT NULL,
    data_type         VARCHAR(20) NOT NULL DEFAULT 'STRING', -- STRING | NUMBER | BOOLEAN | JSON
    description       VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (machine_id, setting_key)
);

COMMENT ON TABLE machine_settings IS 'Per-machine configurable thresholds (overrides system defaults).';
COMMENT ON COLUMN machine_settings.data_type IS 'STRING | NUMBER | BOOLEAN | JSON';

CREATE TABLE IF NOT EXISTS system_settings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID REFERENCES organizations(id) ON DELETE CASCADE, -- NULL = global setting
    setting_key       VARCHAR(100) NOT NULL,
    setting_value     TEXT NOT NULL,
    data_type         VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description       VARCHAR(500),
    is_sensitive      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, setting_key)
);

-- Global settings (NULL org) must be globally unique per key.
CREATE UNIQUE INDEX uq_system_settings_key_global ON system_settings(setting_key) WHERE organization_id IS NULL;

COMMENT ON TABLE system_settings IS 'Platform-level configuration (key-value). NULL organization_id = global setting.';
