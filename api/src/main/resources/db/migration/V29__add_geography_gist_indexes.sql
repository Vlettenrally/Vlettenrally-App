-- Replace geometry GIST indexes with geography-cast GIST indexes
-- ST_DWithin queries now cast to ::geography so the distance parameter is in meters.
-- Geography GIST indexes allow PostGIS to use index scans for these queries.

-- location_data: replace geometry index with geography index
DROP INDEX IF EXISTS idx_location_data_location;
CREATE INDEX idx_location_data_location_geog ON location_data USING GIST ((location::geography));

-- checkpoints: replace geometry index with geography index
DROP INDEX IF EXISTS idx_checkpoints_location;
CREATE INDEX idx_checkpoints_location_geog ON checkpoints USING GIST ((location::geography));

