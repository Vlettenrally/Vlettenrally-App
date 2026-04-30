-- Replace rectangle zone type with polygon zone type using native PostGIS geometry.

-- 1. Add polygon geometry column
ALTER TABLE checkpoints ADD COLUMN zone_polygon GEOMETRY(Polygon, 4326);

-- 2. Convert existing rectangle zones to polygons
UPDATE checkpoints
SET zone_polygon = ST_SetSRID(
    ST_MakePolygon(
        ST_MakeLine(ARRAY[
            ST_MakePoint(bounds_west, bounds_north),
            ST_MakePoint(bounds_east, bounds_north),
            ST_MakePoint(bounds_east, bounds_south),
            ST_MakePoint(bounds_west, bounds_south),
            ST_MakePoint(bounds_west, bounds_north)
        ])
    ), 4326),
    zone_type = 'polygon'
WHERE zone_type = 'rectangle'
  AND bounds_north IS NOT NULL
  AND bounds_south IS NOT NULL
  AND bounds_east IS NOT NULL
  AND bounds_west IS NOT NULL;

-- 3. Add spatial index on zone_polygon
CREATE INDEX idx_checkpoints_zone_polygon ON checkpoints USING GIST (zone_polygon);

-- 4. Drop old bounding-box columns
ALTER TABLE checkpoints DROP COLUMN IF EXISTS bounds_north;
ALTER TABLE checkpoints DROP COLUMN IF EXISTS bounds_south;
ALTER TABLE checkpoints DROP COLUMN IF EXISTS bounds_east;
ALTER TABLE checkpoints DROP COLUMN IF EXISTS bounds_west;

