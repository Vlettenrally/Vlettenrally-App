-- Per-game overrides for the location-ingest outlier guards. Each column is nullable:
-- NULL means "inherit the global default" from application.properties
-- (vlettenrally.location.*), a non-null value overrides that guard for this game only.
-- These are read on every ingested fix, so the resolved thresholds are cached per replica.
ALTER TABLE games ADD COLUMN loc_min_interval_seconds BIGINT;
ALTER TABLE games ADD COLUMN loc_max_speed_kmh DOUBLE PRECISION;
ALTER TABLE games ADD COLUMN loc_boat_max_speed_kmh DOUBLE PRECISION;
ALTER TABLE games ADD COLUMN loc_turn_angle_threshold_degrees DOUBLE PRECISION;
ALTER TABLE games ADD COLUMN loc_min_bearing_segment_meters DOUBLE PRECISION;
ALTER TABLE games ADD COLUMN loc_max_accuracy_meters DOUBLE PRECISION;
ALTER TABLE games ADD COLUMN loc_stale_fix_seconds BIGINT;
