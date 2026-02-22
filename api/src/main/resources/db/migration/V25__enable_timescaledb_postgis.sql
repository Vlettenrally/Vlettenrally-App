-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis CASCADE;

-- Enable PostGIS raster (optional, for advanced spatial operations)
-- CREATE EXTENSION IF NOT EXISTS postgis_raster CASCADE;

-- Verify extensions are installed
-- SELECT * FROM pg_extension WHERE extname IN ('timescaledb', 'postgis');

