-- Team rivals (ManyToMany self-reference, unidirectional)
-- Each team manages their own list of rivals independently
CREATE TABLE team_rivals (
    team_id UUID NOT NULL,
    rival_team_id UUID NOT NULL,
    
    PRIMARY KEY (team_id, rival_team_id),
    
    CONSTRAINT fk_team_rivals_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_rivals_rival FOREIGN KEY (rival_team_id) REFERENCES teams(id) ON DELETE CASCADE,
    
    -- Prevent a team from being its own rival
    CONSTRAINT chk_no_self_rival CHECK (team_id != rival_team_id)
);

-- Indexes
CREATE INDEX idx_team_rivals_team_id ON team_rivals(team_id);
CREATE INDEX idx_team_rivals_rival_team_id ON team_rivals(rival_team_id);
