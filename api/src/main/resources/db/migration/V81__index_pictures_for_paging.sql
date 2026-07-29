-- Support keyset pagination of the picture lists.
--
-- The admin tabs and the players' photowall now page with a (sort key, id) cursor, which makes the
-- sort order load-bearing: without a matching index Postgres sorts the whole game's pictures on
-- every page request. The id is part of each index because it is part of the cursor -- it makes the
-- ordering total so rows with an identical timestamp cannot straddle a page boundary.
--
-- created_at had no index at all before this.

-- "All pictures" tab: ORDER BY created_at DESC, id DESC, filtered by game (and optionally status,
-- which is selective enough to filter after the index scan).
CREATE INDEX IF NOT EXISTS idx_pictures_game_created
    ON pictures (game_id, created_at DESC, id DESC);

-- Approved tab and photowall: ORDER BY uploaded_at DESC, id DESC filtered by game + status.
-- Also serves the moderation queue's ASC scan (Postgres can read an index backwards).
CREATE INDEX IF NOT EXISTS idx_pictures_game_status_uploaded
    ON pictures (game_id, status, uploaded_at DESC, id DESC);

-- The existing idx_pictures_moderation (status, uploaded_at) already covers the
-- no-game-filter variants of the moderation queue.
