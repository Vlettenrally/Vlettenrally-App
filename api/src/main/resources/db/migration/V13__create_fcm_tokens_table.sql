-- FCM tokens table (Firebase Cloud Messaging)
CREATE TABLE fcm_tokens (
    id UUID PRIMARY KEY,
    device_registration_id UUID NOT NULL,
    token TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_fcm_token_device FOREIGN KEY (device_registration_id) REFERENCES device_registrations(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_fcm_tokens_device_registration_id ON fcm_tokens(device_registration_id);
CREATE INDEX idx_fcm_tokens_token ON fcm_tokens(token);
