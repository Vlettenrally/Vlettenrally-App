-- total_points on teams was a denormalized running counter kept in sync by
-- PointsLedgerService. It is now replaced by SUM(current_amount) over the
-- point_entries ledger, cached in Valkey by PointsTotalsCache.
ALTER TABLE teams DROP COLUMN total_points;
