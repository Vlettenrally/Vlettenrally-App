-- Fix the location_data compression configuration set in V28.
--
-- V28 used compress_segmentby = 'id, team_id, game_id'. `id` is a random per-row UUID
-- (LocationDataEntity assigns UUID.randomUUID()), so every compressed batch held exactly one
-- row: no compression benefit at all, and the compressed-chunk index leads with `id`, which
-- leaves team_id unusable as a prefix. A per-team "latest fix" lookup that reached past the
-- open chunk therefore had to scan and decompress whole chunks. V50 widened retention from
-- 1 day to 30 days so quiet teams keep a last-known position, which made that scan wider still.
--
-- Segment by team_id alone: it is the only column the hot queries filter on, and grouping a
-- team's consecutive fixes together is also what makes the columnar encoding actually compress.
--
-- NOTE: compression settings are captured per chunk when the chunk is compressed, so existing
-- compressed chunks keep the old layout until they are rewritten. Decompress them here.
-- On a large table this is the slow part of the migration - run it outside a live event.

SELECT remove_compression_policy('location_data', if_exists => TRUE);

DO $$
DECLARE
    c regclass;
BEGIN
    FOR c IN
        SELECT format('%I.%I', chunk_schema, chunk_name)::regclass
        FROM timescaledb_information.chunks
        WHERE hypertable_name = 'location_data' AND is_compressed
    LOOP
        PERFORM decompress_chunk(c);
    END LOOP;
END $$;

ALTER TABLE location_data SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'location_time DESC',
    timescaledb.compress_segmentby = 'team_id'
);

-- New chunks only (existing ones keep their range). The 7-day default meant 30 days of
-- retention lived in ~5 very wide chunks, so walking back to a quiet team's last fix touched
-- enormous chunks; 1-day chunks also let the retention policy drop data promptly.
SELECT set_chunk_time_interval('location_data', INTERVAL '1 day');

-- Was 1 hour in V28. With 1-day chunks a chunk becomes eligible a day after it closes anyway,
-- and the extra headroom keeps the auto-backfill path - which inserts historical fixes and must
-- honour idx_location_data_team_time_unique (V68) - writing into uncompressed chunks.
SELECT add_compression_policy('location_data', INTERVAL '1 day', if_not_exists => TRUE);
