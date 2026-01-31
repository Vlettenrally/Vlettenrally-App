-- Games table
CREATE TABLE games (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    starting_area_active_time TIMESTAMP WITH TIME ZONE,
    start_signal_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_game_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED'))
);

-- Indexes
CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_start_time ON games(start_time);
