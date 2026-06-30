-- Minutes after the end time to keep polling/ingesting locations once the game is COMPLETED (0 = off).
-- Keeps checkpoint-pass detection alive briefly after the end so late-finish automations
-- (a CHECKPOINT_ENTRY rule firing on a pass AFTER the game end) can still fire.
ALTER TABLE games ADD COLUMN location_polling_tail_minutes INTEGER NOT NULL DEFAULT 30;
