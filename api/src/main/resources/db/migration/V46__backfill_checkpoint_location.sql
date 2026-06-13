-- Backfill checkpoints.location from the scalar latitude/longitude columns.
--
-- The geofencing proximity query (CheckpointRepository.findContainingPoint) matches
-- circle checkpoints on the `location` geometry, but checkpoint create/update only ever
-- set latitude/longitude, leaving `location` NULL. The result was that circle checkpoints
-- silently never matched a team's fix. The service now populates `location` on write;
-- this backfills every existing row so already-created checkpoints start matching too.
UPDATE checkpoints
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE location IS NULL
  AND latitude IS NOT NULL
  AND longitude IS NOT NULL;
