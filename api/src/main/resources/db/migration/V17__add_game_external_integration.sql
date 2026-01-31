-- Add external event integration and location tracking configuration to games table
ALTER TABLE games
    ADD COLUMN external_event_id INTEGER,
    ADD COLUMN external_event_name VARCHAR(255),
    ADD COLUMN location_endpoint_url TEXT,
    ADD COLUMN location_polling_frequency INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN location_endpoint_active BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for querying games by external event
CREATE INDEX idx_games_external_event_id ON games(external_event_id);
