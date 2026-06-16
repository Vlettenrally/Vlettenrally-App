-- Per-team "finished" state: a team that has reached its finish zone stops
-- earning automatic points (geofenced checkpoint passes and distance accrual).
-- Set by the FINISH_TEAM automation action or by an admin override.
ALTER TABLE teams ADD COLUMN finished boolean NOT NULL DEFAULT false;
ALTER TABLE teams ADD COLUMN finished_at timestamptz NULL;
