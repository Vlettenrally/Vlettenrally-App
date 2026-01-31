-- Team emergency phone numbers (ElementCollection)
CREATE TABLE team_emergency_phones (
    team_id UUID NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    phone_order INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_emergency_phone_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_team_emergency_phones_team_id ON team_emergency_phones(team_id);
