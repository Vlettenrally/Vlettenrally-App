-- Team participants (ElementCollection)
CREATE TABLE team_participants (
    team_id UUID NOT NULL,
    participant_name VARCHAR(255) NOT NULL,
    participant_order INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_participant_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_team_participants_team_id ON team_participants(team_id);
