-- V11__create_audit_logs.sql
-- Tamper-resistant audit trail, partitioned by quarter.
-- Captures sensitive actions (auth, command issuance, settings changes, etc.).

CREATE TABLE IF NOT EXISTS audit_logs (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID,
    user_id           UUID,
    action            VARCHAR(100) NOT NULL, -- e.g. LOGIN, COMMAND_ISSUE, SETTINGS_UPDATE
    resource_type     VARCHAR(50),           -- e.g. MACHINE, CUSTOMER, USER
    resource_id       UUID,
    ip_address        VARCHAR(45),
    user_agent        TEXT,
    details           JSONB,
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE audit_logs IS 'Tamper-resistant audit trail, partitioned by quarter.';
COMMENT ON COLUMN audit_logs.action IS 'e.g. LOGIN, COMMAND_ISSUE, SETTINGS_UPDATE';

-- Partitions: 2026 Q3/Q4 + 2027 Q1-Q4 (covers current + 5 quarters).
CREATE TABLE audit_logs_2026q3 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE audit_logs_2026q4 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');
CREATE TABLE audit_logs_2027q1 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-01-01') TO ('2027-04-01');
CREATE TABLE audit_logs_2027q2 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-04-01') TO ('2027-07-01');
CREATE TABLE audit_logs_2027q3 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-07-01') TO ('2027-10-01');
CREATE TABLE audit_logs_2027q4 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-10-01') TO ('2028-01-01');
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

-- Per-partition indexes for common audit queries.
CREATE INDEX idx_audit_logs_2026q3_org_user_created ON audit_logs_2026q3(organization_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_2026q4_org_user_created ON audit_logs_2026q4(organization_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q1_org_user_created ON audit_logs_2027q1(organization_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q2_org_user_created ON audit_logs_2027q2(organization_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q3_org_user_created ON audit_logs_2027q3(organization_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q4_org_user_created ON audit_logs_2027q4(organization_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_default_org_user_created ON audit_logs_default(organization_id, user_id, created_at DESC);

CREATE INDEX idx_audit_logs_2026q3_resource ON audit_logs_2026q3(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_logs_2026q4_resource ON audit_logs_2026q4(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q1_resource ON audit_logs_2027q1(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q2_resource ON audit_logs_2027q2(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q3_resource ON audit_logs_2027q3(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_logs_2027q4_resource ON audit_logs_2027q4(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_logs_default_resource ON audit_logs_default(resource_type, resource_id, created_at DESC);
