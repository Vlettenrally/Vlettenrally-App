-- Distance-bonus ledger entries accrue continuously into a single row. Record the
-- credited distance (meters) alongside the points so the admin ledger can show how
-- far a team travelled for those points instead of a meaningless original/adjustment.
-- Nullable: only DISTANCE_BONUS entries carry a value.
ALTER TABLE point_entries ADD COLUMN distance_meters double precision;
