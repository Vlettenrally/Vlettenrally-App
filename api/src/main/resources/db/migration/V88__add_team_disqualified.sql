-- Whether a team has been disqualified. Excludes it from the points-ranked
-- part of the standings (it still shows, sunk to the bottom as DSQ) and
-- stops it accruing further automatic points, same as a finished team.
ALTER TABLE teams ADD COLUMN disqualified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE teams ADD COLUMN disqualified_at TIMESTAMPTZ;
