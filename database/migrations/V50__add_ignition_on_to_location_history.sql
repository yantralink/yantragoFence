-- ACC (ignition) status on location history points.
-- Populated by LocationConsumer from LocationMessage.ignitionOn so engine
-- state can be replayed alongside the GPS trail (engine-hours analytics).
-- NULL = unknown (no ACC-capable packet for that point, or legacy rows).
ALTER TABLE location_history ADD COLUMN ignition_on BOOLEAN;
