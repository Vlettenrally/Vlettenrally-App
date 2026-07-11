-- Timestamp of a team's physical check-in (when checked_in transitions to true).
-- The digital check-in window — the period during which a device may still register
-- through the regular game/team picker — is measured from this moment. Cleared back
-- to NULL whenever checked_in is reset to false.
ALTER TABLE teams ADD COLUMN checked_in_at timestamptz NULL;
