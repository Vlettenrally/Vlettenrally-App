-- Whether a team has physically returned its GPS tracker at the end of the game.
-- Independent of tracker_id/tracker_assigned_at (assignment vs. return).
ALTER TABLE teams ADD COLUMN tracker_handed_in BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE teams ADD COLUMN tracker_handed_in_at TIMESTAMPTZ;
