-- V9__create_alerts.sql
-- alert_rules: alert configuration per machine/tenant (thresholds, conditions).
-- alerts: generated alert instances (one per rule violation occurrence).

CREATE TABLE IF NOT EXISTS alert_rules (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id        UUID REFERENCES machines(id) ON DELETE CASCADE, -- NULL = org-wide rule
    name              VARCHAR(255) NOT NULL,
    alert_type        VARCHAR(50) NOT NULL, -- LOW_BATTERY | OFFLINE | GEOFENCE | VOLTAGE_DROP | SOS | ...
    condition_config  JSONB NOT NULL,       -- e.g. {"threshold": 20, "duration_minutes": 30}
    severity          VARCHAR(20) NOT NULL DEFAULT 'WARNING', -- INFO | WARNING | CRITICAL
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE alert_rules IS 'Alert configuration per machine/tenant. NULL machine_id = org-wide rule.';
COMMENT ON COLUMN alert_rules.severity IS 'INFO | WARNING | CRITICAL';
COMMENT ON COLUMN alert_rules.condition_config IS 'JSON config (e.g. {"threshold": 20, "duration_minutes": 30}).';

CREATE TABLE IF NOT EXISTS alerts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    alert_rule_id     UUID REFERENCES alert_rules(id) ON DELETE SET NULL,
    machine_id        UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    device_id         UUID REFERENCES devices(id) ON DELETE SET NULL,
    alert_type        VARCHAR(50) NOT NULL,
    severity          VARCHAR(20) NOT NULL, -- INFO | WARNING | CRITICAL
    message           TEXT NOT NULL,
    is_acknowledged   BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    acknowledged_at   TIMESTAMPTZ,
    triggered_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE alerts IS 'Generated alert instances (one per rule violation occurrence).';
COMMENT ON COLUMN alerts.severity IS 'INFO | WARNING | CRITICAL';
