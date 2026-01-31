-- Teams table
CREATE TABLE teams (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    boat_number VARCHAR(50),
    device_id UUID,
    checked_in BOOLEAN NOT NULL DEFAULT FALSE,
    total_points INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_team_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_device FOREIGN KEY (device_id) REFERENCES device_registrations(id) ON DELETE SET NULL
);

-- Now add the foreign key from device_registrations to teams
ALTER TABLE device_registrations 
    ADD CONSTRAINT fk_device_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE;

-- Indexes
CREATE INDEX idx_teams_game_id ON teams(game_id);
CREATE INDEX idx_teams_device_id ON teams(device_id);
CREATE INDEX idx_teams_boat_number ON teams(boat_number);
CREATE INDEX idx_teams_checked_in ON teams(checked_in);
