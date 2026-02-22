-- Create location_data table with PostGIS geometry
CREATE TABLE location_data (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    game_id UUID NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,  -- PostGIS Point with WGS84 (EPSG:4326)
    location_time TIMESTAMP WITH TIME ZONE NOT NULL,  -- API location_time field
    accuracy DECIMAL(8, 2),  -- GPS accuracy in meters
    altitude DECIMAL(8, 2),  -- Altitude in meters
    speed DECIMAL(5, 2),  -- Speed in km/h
    heading DECIMAL(5, 2),  -- Heading/bearing in degrees
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_location_data_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_location_data_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);

-- Convert location_data to a TimescaleDB hypertable
SELECT create_hypertable('location_data', 'location_time', if_not_exists => TRUE);

-- Create spatial index for geofencing queries (50m radius proximity detection)
CREATE INDEX idx_location_data_location ON location_data USING GIST (location);

-- Create index for team_id and location_time (common query pattern)
CREATE INDEX idx_location_data_team_time ON location_data (team_id, location_time DESC);

-- Create index for game_id and location_time
CREATE INDEX idx_location_data_game_time ON location_data (game_id, location_time DESC);

-- Set up automatic compression: compress chunks older than 1 hour to 5-minute intervals
-- This reduces storage while maintaining recent detailed location history
SELECT add_compression_policy('location_data', INTERVAL '1 hour', if_not_exists => TRUE);

-- Set up automatic retention policy: delete data older than 1 day
SELECT add_retention_policy('location_data', INTERVAL '1 day', if_not_exists => TRUE);

