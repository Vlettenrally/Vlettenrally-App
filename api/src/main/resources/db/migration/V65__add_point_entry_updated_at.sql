-- Add updated_at to point_entries to track the last modification time,
-- whether from an admin edit or from system accrual (distance bonus).
-- Sort the ledger by updated_at DESC so continuously-accrued entries
-- stay near the top rather than sinking to their original creation time.

ALTER TABLE point_entries ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE point_entries SET updated_at = created_at;
ALTER TABLE point_entries ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_point_entries_game_updated ON point_entries (game_id, updated_at DESC);
