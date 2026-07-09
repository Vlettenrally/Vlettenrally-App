-- Uniqueness on (team_id, location_time) for location_data.
--
-- Backfilling historical tracks (Legendstracking /participant_track) inserts many fixes at once;
-- the backfill service dedupes against stored fixes in memory and runs under a per-team Valkey lock
-- (the live poller skips a team currently being backfilled), but a read-then-insert is not atomic.
-- This index is the database-level safety net that makes a concurrent same-timestamp insert fail
-- instead of silently creating a duplicate fix (which would inflate travelled distance).
--
-- location_time is the TimescaleDB hypertable partition column, so it MUST be part of any unique
-- index on this table. The existing primary key is (id, location_time) with a random id, which does
-- not prevent duplicate (team_id, location_time) rows — hence this additional constraint.
--
-- NOTE: the live ingest path never stores two fixes with the same (team_id, location_time)
-- (the outlier guard drops any fix whose timestamp does not strictly advance), so no de-duplication
-- of existing rows is expected to be required. If a pre-existing duplicate ever blocks creation,
-- collapse duplicates (keep one row per team_id/location_time) before re-running this migration.
CREATE UNIQUE INDEX IF NOT EXISTS idx_location_data_team_time_unique
    ON location_data (team_id, location_time);
