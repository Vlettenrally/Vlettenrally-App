-- Global tracker pool: replaces the per-game game_trackers pool. Membership is
-- global; assignment to a team still lives on teams.tracker_id. Status columns
-- (battery, last seen, last position) are updated by the location poller.
CREATE TABLE trackers (
    id                 UUID PRIMARY KEY,
    tracker_id         VARCHAR(64) NOT NULL UNIQUE,
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    battery_percentage INT NULL,
    last_seen_at       TIMESTAMPTZ NULL,
    last_latitude      DOUBLE PRECISION NULL,
    last_longitude     DOUBLE PRECISION NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Union of the per-game pools and directly-assigned team trackers (defensive:
-- per-team assignment required pool membership, but teams.tracker_id is the
-- source of truth for assignments).
INSERT INTO trackers (id, tracker_id)
SELECT gen_random_uuid(), tracker_id FROM (
    SELECT DISTINCT tracker_id FROM game_trackers
    UNION
    SELECT DISTINCT tracker_id FROM teams WHERE tracker_id IS NOT NULL
) AS ids
ON CONFLICT (tracker_id) DO NOTHING;
