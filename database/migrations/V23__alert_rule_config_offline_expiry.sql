-- V23__alert_rule_config_offline_expiry.sql
-- Phase 2: typed rule configuration, audit history, offline/expiry state tracking.
-- All changes are additive.

-- ===== 1. Extend alert_rules with lifecycle config =====
-- Sustain window: condition must persist for N minutes before opening an incident.
-- Recovery window: condition must clear for N minutes before resolving an incident.
-- Escalation severity: optional higher severity after escalation_minutes.

ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS sustain_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS recovery_minutes INTEGER NOT NULL DEFAULT 5;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS escalation_minutes INTEGER;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS escalation_severity VARCHAR(20);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS rule_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

COMMENT ON COLUMN alert_rules.sustain_minutes IS 'Condition must persist this many minutes before opening an incident. 0 = immediate.';
COMMENT ON COLUMN alert_rules.recovery_minutes IS 'Condition must clear this many minutes before resolving an incident.';
COMMENT ON COLUMN alert_rules.escalation_minutes IS 'Optional: escalate severity after this many minutes of violation.';
COMMENT ON COLUMN alert_rules.escalation_severity IS 'Optional: severity to escalate to (INFO|WARNING|CRITICAL).';
COMMENT ON COLUMN alert_rules.rule_version IS 'Rule config version for audit trail. Incremented on each update.';

-- ===== 2. Alert rule audit history =====
-- Tracks every create/update/delete of alert rules for compliance.

CREATE TABLE IF NOT EXISTS alert_rule_audit (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id           UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    action            VARCHAR(20) NOT NULL, -- CREATE | UPDATE | DELETE | ACTIVATE | DEACTIVATE
    changed_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    old_config        JSONB,
    new_config        JSONB,
    old_version       INTEGER,
    new_version       INTEGER,
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE alert_rule_audit IS 'Audit trail for alert rule changes.';
CREATE INDEX IF NOT EXISTS idx_rule_audit_rule_id ON alert_rule_audit (rule_id);
CREATE INDEX IF NOT EXISTS idx_rule_audit_org_id ON alert_rule_audit (organization_id);

-- ===== 3. Offline detection state =====
-- One row per device. Tracks the offline detection lifecycle.
-- Uses row claiming for multi-instance-safe scheduling.

CREATE TABLE IF NOT EXISTS offline_detection_state (
    device_id             UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    organization_id       UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id            UUID REFERENCES machines(id) ON DELETE SET NULL,
    last_heartbeat_at     TIMESTAMPTZ,
    grace_until           TIMESTAMPTZ,                 -- reconnect grace period
    offline_alert_open    BOOLEAN NOT NULL DEFAULT FALSE,
    offline_alert_id      UUID,                        -- the open DEVICE_OFFLINE alert ID
    last_evaluated_at     TIMESTAMPTZ,
    claim_lease_until     TIMESTAMPTZ,                 -- scheduler lease
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE offline_detection_state IS 'Per-device offline detection lifecycle state. Multi-instance-safe via row claiming.';

CREATE INDEX IF NOT EXISTS idx_offline_state_org ON offline_detection_state (organization_id);
CREATE INDEX IF NOT EXISTS idx_offline_state_eval ON offline_detection_state (last_evaluated_at)
    WHERE offline_alert_open = FALSE;

-- ===== 4. Expiry milestone state =====
-- One row per device recharge cycle. Tracks which expiry milestones have fired.
-- Uses row claiming for multi-instance-safe scheduling.

CREATE TABLE IF NOT EXISTS expiry_detection_state (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_id             UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    machine_id            UUID REFERENCES machines(id) ON DELETE SET NULL,
    recharge_id           UUID REFERENCES recharges(id) ON DELETE SET NULL,
    valid_until           TIMESTAMPTZ NOT NULL,
    cycle_key             VARCHAR(100) NOT NULL,        -- unique per expiry cycle (device_id:valid_until)
    milestones_fired      JSONB NOT NULL DEFAULT '[]',  -- array of fired milestone days
    alert_open            BOOLEAN NOT NULL DEFAULT FALSE,
    alert_id              UUID,                         -- the open SIM_EXPIRY alert ID
    resolved_at           TIMESTAMPTZ,                   -- set when renewed or expired
    last_evaluated_at     TIMESTAMPTZ,
    claim_lease_until     TIMESTAMPTZ,                  -- scheduler lease
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cycle_key)
);

COMMENT ON TABLE expiry_detection_state IS 'Per-device-recharge expiry milestone tracking. Multi-instance-safe via row claiming.';

CREATE INDEX IF NOT EXISTS idx_expiry_state_org ON expiry_detection_state (organization_id);
CREATE INDEX IF NOT EXISTS idx_expiry_state_eval ON expiry_detection_state (last_evaluated_at)
    WHERE resolved_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_expiry_state_device ON expiry_detection_state (device_id)
    WHERE resolved_at IS NULL;
