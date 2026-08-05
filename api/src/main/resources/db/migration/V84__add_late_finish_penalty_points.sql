-- Flat point deduction for a team that finishes after the game's end_time.
-- 0 (the default) disables the penalty for that game.
ALTER TABLE games ADD COLUMN late_finish_penalty_points INTEGER NOT NULL DEFAULT 0;
