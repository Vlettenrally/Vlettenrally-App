-- Achievements table
CREATE TABLE achievements (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    checkpoint_id UUID NOT NULL,
    points_earned INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMP WITH TIME ZONE,
    verified_by UUID,
    notes TEXT,
    
    CONSTRAINT fk_achievement_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_verified_by FOREIGN KEY (verified_by) REFERENCES admins(id) ON DELETE SET NULL,
    
    CONSTRAINT chk_achievement_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    
    -- One achievement per team per checkpoint
    CONSTRAINT uq_team_checkpoint UNIQUE (team_id, checkpoint_id)
);

-- Indexes
CREATE INDEX idx_achievements_team_id ON achievements(team_id);
CREATE INDEX idx_achievements_checkpoint_id ON achievements(checkpoint_id);
CREATE INDEX idx_achievements_status ON achievements(status);
