-- V7__create_telemetry_tables.sql
-- Time-series telemetry: voltage_readings, battery_readings, gsm_readings.
-- All partitioned by month (same strategy as location_history).
-- High-volume writes use batch inserts (see LocationPersistenceService pattern).

-- ===== voltage_readings =====
CREATE TABLE IF NOT EXISTS voltage_readings (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    device_id         UUID NOT NULL,
    machine_id        UUID,
    imei              VARCHAR(20) NOT NULL,
    voltage           DOUBLE PRECISION NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

COMMENT ON TABLE voltage_readings IS 'Time-series voltage readings, partitioned by month.';

CREATE TABLE voltage_readings_202609 PARTITION OF voltage_readings
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE voltage_readings_202610 PARTITION OF voltage_readings
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE voltage_readings_202611 PARTITION OF voltage_readings
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE voltage_readings_202612 PARTITION OF voltage_readings
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE voltage_readings_202701 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE voltage_readings_202702 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE voltage_readings_202703 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE voltage_readings_202704 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE voltage_readings_202705 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE voltage_readings_202706 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE voltage_readings_202707 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE voltage_readings_202708 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE voltage_readings_202709 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE voltage_readings_202710 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
CREATE TABLE voltage_readings_202711 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-11-01') TO ('2027-12-01');
CREATE TABLE voltage_readings_202712 PARTITION OF voltage_readings
    FOR VALUES FROM ('2027-12-01') TO ('2028-01-01');
CREATE TABLE voltage_readings_default PARTITION OF voltage_readings DEFAULT;

-- ===== battery_readings =====
CREATE TABLE IF NOT EXISTS battery_readings (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    device_id         UUID NOT NULL,
    machine_id        UUID,
    imei              VARCHAR(20) NOT NULL,
    battery_pct       DOUBLE PRECISION NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

COMMENT ON TABLE battery_readings IS 'Time-series battery readings, partitioned by month.';

CREATE TABLE battery_readings_202609 PARTITION OF battery_readings
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE battery_readings_202610 PARTITION OF battery_readings
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE battery_readings_202611 PARTITION OF battery_readings
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE battery_readings_202612 PARTITION OF battery_readings
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE battery_readings_202701 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE battery_readings_202702 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE battery_readings_202703 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE battery_readings_202704 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE battery_readings_202705 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE battery_readings_202706 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE battery_readings_202707 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE battery_readings_202708 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE battery_readings_202709 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE battery_readings_202710 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
CREATE TABLE battery_readings_202711 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-11-01') TO ('2027-12-01');
CREATE TABLE battery_readings_202712 PARTITION OF battery_readings
    FOR VALUES FROM ('2027-12-01') TO ('2028-01-01');
CREATE TABLE battery_readings_default PARTITION OF battery_readings DEFAULT;

-- ===== gsm_readings =====
CREATE TABLE IF NOT EXISTS gsm_readings (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    device_id         UUID NOT NULL,
    machine_id        UUID,
    imei              VARCHAR(20) NOT NULL,
    gsm_signal        INTEGER NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

COMMENT ON TABLE gsm_readings IS 'Time-series GSM signal readings, partitioned by month.';

CREATE TABLE gsm_readings_202609 PARTITION OF gsm_readings
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE gsm_readings_202610 PARTITION OF gsm_readings
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE gsm_readings_202611 PARTITION OF gsm_readings
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE gsm_readings_202612 PARTITION OF gsm_readings
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE gsm_readings_202701 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE gsm_readings_202702 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE gsm_readings_202703 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE gsm_readings_202704 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE gsm_readings_202705 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE gsm_readings_202706 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE gsm_readings_202707 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE gsm_readings_202708 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE gsm_readings_202709 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE gsm_readings_202710 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
CREATE TABLE gsm_readings_202711 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-11-01') TO ('2027-12-01');
CREATE TABLE gsm_readings_202712 PARTITION OF gsm_readings
    FOR VALUES FROM ('2027-12-01') TO ('2028-01-01');
CREATE TABLE gsm_readings_default PARTITION OF gsm_readings DEFAULT;
