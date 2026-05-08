-- Replace the static points_earned snapshot with bonus_points only.
-- The base point value comes from the checkpoint at lookup time, so the
-- achievement only needs to remember any extra bonus the operator awarded.

ALTER TABLE achievements ADD COLUMN bonus_points INTEGER NOT NULL DEFAULT 0;

ALTER TABLE achievements DROP COLUMN points_earned;
