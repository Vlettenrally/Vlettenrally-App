-- PostgreSQL initialization script for TimescaleDB development
-- This script ensures PostGIS is enabled and ready for use

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis CASCADE;
CREATE EXTENSION IF NOT EXISTS postgis_raster CASCADE;

-- Enable TimescaleDB extension (should already be installed in timescaledb image)
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Log successful initialization
SELECT 'PostGIS and TimescaleDB extensions enabled successfully' AS status;

