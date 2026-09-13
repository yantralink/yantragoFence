-- V47: Customer-level default settings for theft protection (Phase 11).
--
-- Stores per-customer defaults for geofence radius and speed threshold.
-- When a customer enables theft protection for a new machine, these defaults
-- are used instead of the system defaults (200m / 10 km/h).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Per AGENTS.md rule 19: UUIDs as primary keys.

CREATE TABLE IF NOT EXISTS customer_settings (
    id                              UUID NOT NULL DEFAULT gen_random_uuid(),
    customer_id                     UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    default_geofence_radius_meters  INTEGER NOT NULL DEFAULT 200,
    default_speed_threshold_kmh     INTEGER NOT NULL DEFAULT 10,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

-- One settings row per customer
CREATE UNIQUE INDEX idx_customer_settings_customer
    ON customer_settings(customer_id);

COMMENT ON TABLE customer_settings IS 'Per-customer defaults for theft protection (geofence radius, speed threshold).';
COMMENT ON COLUMN customer_settings.default_geofence_radius_meters IS 'Default geofence radius in meters (50-1000). Used when customer enables protection.';
COMMENT ON COLUMN customer_settings.default_speed_threshold_kmh IS 'Default speed threshold in km/h (1-30). Used for MACHINE_MOVING alert rule.';
