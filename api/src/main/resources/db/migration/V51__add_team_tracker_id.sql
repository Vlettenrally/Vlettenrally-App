-- Physical GPS tracker assigned to a team. A batch of tracker IDs from the
-- external provider is pasted in and auto-distributed across teams; the
-- mapping is exported back to the provider. Independent of traccar_identifier
-- (the app/secret location pipeline).
ALTER TABLE teams ADD COLUMN tracker_id VARCHAR(64);

-- A given physical tracker may belong to at most one boat within a game.
CREATE UNIQUE INDEX idx_teams_tracker_id ON teams(game_id, tracker_id) WHERE tracker_id IS NOT NULL;
