-- WebSocket registrations table
CREATE TABLE websocket_registrations (
    id UUID PRIMARY KEY,
    device_registration_id UUID NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    disconnected_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT fk_websocket_device FOREIGN KEY (device_registration_id) REFERENCES device_registrations(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_websocket_registrations_device_registration_id ON websocket_registrations(device_registration_id);
CREATE INDEX idx_websocket_registrations_session_id ON websocket_registrations(session_id);
CREATE INDEX idx_websocket_registrations_connected ON websocket_registrations(disconnected_at) WHERE disconnected_at IS NULL;
