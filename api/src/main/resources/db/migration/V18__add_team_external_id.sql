-- Add external_id column to teams table for pre-registration system integration
ALTER TABLE teams
    ADD COLUMN external_id VARCHAR(255);

-- Index for looking up teams by external_id within a game
CREATE INDEX idx_teams_external_id ON teams(external_id);

-- Unique constraint: external_id must be unique within a game
CREATE UNIQUE INDEX idx_teams_game_external_id ON teams(game_id, external_id) 
    WHERE external_id IS NOT NULL;
