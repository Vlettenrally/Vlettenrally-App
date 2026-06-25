-- Pool of physical tracker IDs known for a game. A pasted batch is stored here so
-- that trackers not auto-assigned stay available to pick per team later. The
-- actual assignment lives on teams.tracker_id; availability = pool minus assigned.
CREATE TABLE game_trackers (
    id         UUID PRIMARY KEY,
    game_id    UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    tracker_id VARCHAR(64) NOT NULL,
    UNIQUE (game_id, tracker_id)
);

CREATE INDEX idx_game_trackers_game ON game_trackers(game_id);
