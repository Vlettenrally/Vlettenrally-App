-- Add checked_in_digitally column to teams table
-- This tracks whether a team checked in digitally (via participant flow)
-- vs manual check-in by admin

ALTER TABLE teams
    ADD COLUMN checked_in_digitally BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for querying digital check-in status
CREATE INDEX idx_teams_checked_in_digitally ON teams(checked_in_digitally);

-- Update existing teams: if they're checked in, assume it was digital
-- (this is for migration purposes - existing checked-in teams were likely digital)
UPDATE teams
SET checked_in_digitally = TRUE
WHERE checked_in = TRUE;

