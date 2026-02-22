-- Configure TimescaleDB compression policy for location_data hypertable
-- This migration enables columnstore compression and sets up automatic compression

-- Enable columnstore compression for the hypertable (required before compression policy)
ALTER TABLE location_data SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'location_time DESC',
    timescaledb.compress_segmentby = 'id, team_id, game_id'
);

-- Set up automatic compression: compress chunks older than 1 hour to 5-minute intervals
-- This reduces storage while maintaining recent detailed location history
SELECT add_compression_policy('location_data', INTERVAL '1 hour', if_not_exists => TRUE);

-- Set up automatic retention policy: delete data older than 1 day
SELECT add_retention_policy('location_data', INTERVAL '1 day', if_not_exists => TRUE);

