-- Messages table
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL,
    team_id UUID NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT,
    icon VARCHAR(100),
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered BOOLEAN NOT NULL DEFAULT TRUE,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    transmission_error TEXT,
    source VARCHAR(20),
    sent_by_admin_id UUID,
    batch_id UUID,
    
    CONSTRAINT fk_message_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sent_by_admin FOREIGN KEY (sent_by_admin_id) REFERENCES admins(id) ON DELETE SET NULL,
    
    CONSTRAINT chk_message_type CHECK (message_type IN ('GENERAL', 'COUNTDOWN', 'CHECKPOINT', 'RALLYDESK', 'ACHIEVEMENT', 'RIVAL')),
    CONSTRAINT chk_message_source CHECK (source IS NULL OR source IN ('ADMIN', 'SYSTEM', 'AUTOMATION'))
);

-- Indexes
CREATE INDEX idx_messages_team_id ON messages(team_id);
CREATE INDEX idx_messages_game_id ON messages(game_id);
CREATE INDEX idx_messages_batch_id ON messages(batch_id);
CREATE INDEX idx_messages_sent_at ON messages(sent_at);
CREATE INDEX idx_messages_game_source ON messages(game_id, source);
CREATE INDEX idx_messages_delivered ON messages(delivered);
