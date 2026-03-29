-- Add external_id column to checkpoints table for location tracker integration
ALTER TABLE checkpoints
    ADD COLUMN external_id VARCHAR(255);

-- Unique constraint: external_id must be unique within a game
CREATE UNIQUE INDEX idx_checkpoints_game_external_id ON checkpoints(game_id, external_id)
    WHERE external_id IS NOT NULL;

