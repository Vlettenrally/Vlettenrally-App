-- Per-team Traccar tracking: opt-in flag plus a long-lived secret identifier
-- the Traccar Client app uses to report locations to our ingestion endpoint.
ALTER TABLE teams ADD COLUMN traccar_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE teams ADD COLUMN traccar_identifier VARCHAR(64);

CREATE UNIQUE INDEX idx_teams_traccar_identifier ON teams(traccar_identifier);
