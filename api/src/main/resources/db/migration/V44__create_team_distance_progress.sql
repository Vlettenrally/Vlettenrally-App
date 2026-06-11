-- Per-team accumulator for travelled distance computed from stored location fixes.
-- Replaces the provider-reported distance as the source for DISTANCE_TRAVELED points.
-- One row per team: the held anchor (last counted fix), the running cumulative
-- total fed to the automation engine, and a cursor (GPS time of the last consumed
-- fix) so each poll only reads newer fixes.
CREATE TABLE team_distance_progress (
    team_id             UUID PRIMARY KEY,
    anchor_lon          DOUBLE PRECISION,             -- null until first fix
    anchor_lat          DOUBLE PRECISION,
    cumulative_meters   NUMERIC NOT NULL DEFAULT 0,
    last_processed_time TIMESTAMP WITH TIME ZONE,      -- cursor: GPS time of last consumed fix
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_team_distance_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);
