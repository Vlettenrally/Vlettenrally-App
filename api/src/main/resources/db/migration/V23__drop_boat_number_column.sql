-- Drop boat_number column and its index from teams table
-- Boat numbers are no longer used in the application

DROP INDEX IF EXISTS idx_teams_boat_number;

ALTER TABLE teams DROP COLUMN IF EXISTS boat_number;

