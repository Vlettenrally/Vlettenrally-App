-- Checkpoints table
CREATE TABLE checkpoints (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    opening_time TIMESTAMP WITH TIME ZONE,
    closing_time TIMESTAMP WITH TIME ZONE,
    points INTEGER NOT NULL DEFAULT 0,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_checkpoint_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_checkpoints_game_id ON checkpoints(game_id);
CREATE INDEX idx_checkpoints_opening_time ON checkpoints(opening_time);
