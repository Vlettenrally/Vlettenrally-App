-- Message transmission channels (ElementCollection of enum)
CREATE TABLE message_transmission_channels (
    message_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    
    CONSTRAINT fk_transmission_channel_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT chk_channel CHECK (channel IN ('WEBSOCKET', 'PUSH', 'SMS'))
);

-- Indexes
CREATE INDEX idx_message_transmission_channels_message_id ON message_transmission_channels(message_id);
