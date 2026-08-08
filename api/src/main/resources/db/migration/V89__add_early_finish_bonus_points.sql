-- Flat point bonus for a team that finishes in the closing stretch of the game: at or after
-- (end_time - early_finish_bonus_minutes) and at or before end_time. Finishing earlier than
-- that window earns no bonus.
ALTER TABLE games ADD COLUMN early_finish_bonus_points INTEGER NOT NULL DEFAULT 50;
ALTER TABLE games ADD COLUMN early_finish_bonus_minutes INTEGER NOT NULL DEFAULT 60;
