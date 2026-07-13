-- The per-game tracker pool is superseded by the global trackers table (V75
-- migrated the data). Assignment stays on teams.tracker_id.
DROP TABLE game_trackers;
