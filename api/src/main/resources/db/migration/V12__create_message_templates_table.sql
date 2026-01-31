-- Message templates table (global, not game-scoped)
CREATE TABLE message_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    icon VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_template_message_type CHECK (message_type IN ('GENERAL', 'COUNTDOWN', 'CHECKPOINT', 'RALLYDESK', 'ACHIEVEMENT', 'RIVAL'))
);

-- Indexes
CREATE INDEX idx_message_templates_message_type ON message_templates(message_type);
