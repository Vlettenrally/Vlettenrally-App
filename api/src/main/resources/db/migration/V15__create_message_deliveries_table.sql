-- Message deliveries table (per-team delivery tracking)
CREATE TABLE message_deliveries (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    team_id UUID NOT NULL,
    delivered BOOLEAN NOT NULL DEFAULT FALSE,
    transmission_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_delivery_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    
    -- One delivery record per message per team
    CONSTRAINT uq_message_team_delivery UNIQUE (message_id, team_id)
);

-- Indexes
CREATE INDEX idx_message_deliveries_message_id ON message_deliveries(message_id);
CREATE INDEX idx_message_deliveries_team_id ON message_deliveries(team_id);
CREATE INDEX idx_message_deliveries_delivered ON message_deliveries(delivered);
