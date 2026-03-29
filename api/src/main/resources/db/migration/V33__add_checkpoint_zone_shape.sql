-- Add configurable zone shape to checkpoints.
-- zone_type: 'circle' (default, uses radius_meters) or 'rectangle' (uses bounds columns).
ALTER TABLE checkpoints ADD COLUMN zone_type VARCHAR(20) NOT NULL DEFAULT 'circle';
ALTER TABLE checkpoints ADD COLUMN radius_meters DOUBLE PRECISION NOT NULL DEFAULT 50.0;

-- Bounding-box columns for rectangle zones (nullable — only used when zone_type = 'rectangle')
ALTER TABLE checkpoints ADD COLUMN bounds_north DOUBLE PRECISION;
ALTER TABLE checkpoints ADD COLUMN bounds_south DOUBLE PRECISION;
ALTER TABLE checkpoints ADD COLUMN bounds_east DOUBLE PRECISION;
ALTER TABLE checkpoints ADD COLUMN bounds_west DOUBLE PRECISION;

