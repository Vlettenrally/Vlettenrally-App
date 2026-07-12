-- Moment the team's current physical tracker was (re)assigned to it. Location
-- backfill uses this as a lower bound: the provider track spans the tracker's
-- whole history, and points recorded before this team held the tracker belong
-- to its previous holder and must never be imported. NULL (pre-existing rows,
-- never assigned, or tracker cleared) means no extra bound.
ALTER TABLE teams ADD COLUMN tracker_assigned_at timestamptz NULL;
