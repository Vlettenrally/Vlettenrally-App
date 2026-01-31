-- Message delivery transmission channels (ElementCollection of enum)
CREATE TABLE message_delivery_channels (
    message_delivery_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    
    CONSTRAINT fk_delivery_channel_delivery FOREIGN KEY (message_delivery_id) REFERENCES message_deliveries(id) ON DELETE CASCADE,
    CONSTRAINT chk_delivery_channel CHECK (channel IN ('WEBSOCKET', 'PUSH', 'SMS'))
);

-- Indexes
CREATE INDEX idx_message_delivery_channels_delivery_id ON message_delivery_channels(message_delivery_id);
