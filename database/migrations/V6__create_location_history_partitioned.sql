-- V6__create_location_history_partitioned.sql
-- Historical GPS locations, partitioned by month for query performance at scale.
-- Pattern adapted from HarvestTracker's vehicle_location_history partitioning.
--
-- NOTE: For a partitioned table, the PRIMARY KEY must include the partition key.
-- Future partitions are created by database/partitions/create_monthly_partitions.sql.

CREATE TABLE IF NOT EXISTS location_history (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    device_id         UUID NOT NULL,
    machine_id        UUID,
    imei              VARCHAR(20) NOT NULL,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    speed             DOUBLE PRECISION,
    course            DOUBLE PRECISION,
    location_geo      GEOGRAPHY(POINT, 4326),
    recorded_at       TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

COMMENT ON TABLE location_history IS 'Historical GPS locations, partitioned by month.';

-- Partitions: 2026-09 through 2027-12 (16 months from current).
CREATE TABLE location_history_202609 PARTITION OF location_history
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE location_history_202610 PARTITION OF location_history
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE location_history_202611 PARTITION OF location_history
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE location_history_202612 PARTITION OF location_history
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE location_history_202701 PARTITION OF location_history
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE location_history_202702 PARTITION OF location_history
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE location_history_202703 PARTITION OF location_history
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE location_history_202704 PARTITION OF location_history
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE location_history_202705 PARTITION OF location_history
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE location_history_202706 PARTITION OF location_history
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE location_history_202707 PARTITION OF location_history
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE location_history_202708 PARTITION OF location_history
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE location_history_202709 PARTITION OF location_history
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE location_history_202710 PARTITION OF location_history
    FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
CREATE TABLE location_history_202711 PARTITION OF location_history
    FOR VALUES FROM ('2027-11-01') TO ('2027-12-01');
CREATE TABLE location_history_202712 PARTITION OF location_history
    FOR VALUES FROM ('2027-12-01') TO ('2028-01-01');

-- Default partition for out-of-range data (should be rare; alerts on insert here).
CREATE TABLE location_history_default PARTITION OF location_history DEFAULT;

-- Per-partition composite indexes (machine_id + recorded_at DESC).
-- Created here so they exist on all initial partitions.
CREATE INDEX idx_location_history_202609_machine_recorded ON location_history_202609(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202610_machine_recorded ON location_history_202610(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202611_machine_recorded ON location_history_202611(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202612_machine_recorded ON location_history_202612(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202701_machine_recorded ON location_history_202701(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202702_machine_recorded ON location_history_202702(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202703_machine_recorded ON location_history_202703(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202704_machine_recorded ON location_history_202704(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202705_machine_recorded ON location_history_202705(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202706_machine_recorded ON location_history_202706(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202707_machine_recorded ON location_history_202707(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202708_machine_recorded ON location_history_202708(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202709_machine_recorded ON location_history_202709(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202710_machine_recorded ON location_history_202710(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202711_machine_recorded ON location_history_202711(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_202712_machine_recorded ON location_history_202712(machine_id, recorded_at DESC);
CREATE INDEX idx_location_history_default_machine_recorded ON location_history_default(machine_id, recorded_at DESC);

-- Per-partition GiST spatial indexes for radius/polygon queries.
CREATE INDEX idx_location_history_202609_geo ON location_history_202609 USING GIST(location_geo);
CREATE INDEX idx_location_history_202610_geo ON location_history_202610 USING GIST(location_geo);
CREATE INDEX idx_location_history_202611_geo ON location_history_202611 USING GIST(location_geo);
CREATE INDEX idx_location_history_202612_geo ON location_history_202612 USING GIST(location_geo);
CREATE INDEX idx_location_history_202701_geo ON location_history_202701 USING GIST(location_geo);
CREATE INDEX idx_location_history_202702_geo ON location_history_202702 USING GIST(location_geo);
CREATE INDEX idx_location_history_202703_geo ON location_history_202703 USING GIST(location_geo);
CREATE INDEX idx_location_history_202704_geo ON location_history_202704 USING GIST(location_geo);
CREATE INDEX idx_location_history_202705_geo ON location_history_202705 USING GIST(location_geo);
CREATE INDEX idx_location_history_202706_geo ON location_history_202706 USING GIST(location_geo);
CREATE INDEX idx_location_history_202707_geo ON location_history_202707 USING GIST(location_geo);
CREATE INDEX idx_location_history_202708_geo ON location_history_202708 USING GIST(location_geo);
CREATE INDEX idx_location_history_202709_geo ON location_history_202709 USING GIST(location_geo);
CREATE INDEX idx_location_history_202710_geo ON location_history_202710 USING GIST(location_geo);
CREATE INDEX idx_location_history_202711_geo ON location_history_202711 USING GIST(location_geo);
CREATE INDEX idx_location_history_202712_geo ON location_history_202712 USING GIST(location_geo);
CREATE INDEX idx_location_history_default_geo ON location_history_default USING GIST(location_geo);
