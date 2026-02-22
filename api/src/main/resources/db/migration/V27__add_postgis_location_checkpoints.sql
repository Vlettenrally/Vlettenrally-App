-- Add PostGIS geometry column to checkpoints for geofencing (50m radius)
ALTER TABLE checkpoints ADD COLUMN location GEOMETRY(Point, 4326);

-- Populate location from existing latitude/longitude columns
UPDATE checkpoints
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- Add constraint to ensure location is set for active checkpoints
-- (optional, can be enforced in application if some checkpoints don't have coordinates)

-- Create spatial index for geofencing queries
CREATE INDEX idx_checkpoints_location ON checkpoints USING GIST (location);

-- Create index combining game_id and location for efficient queries
CREATE INDEX idx_checkpoints_game_location ON checkpoints (game_id) INCLUDE (location);

