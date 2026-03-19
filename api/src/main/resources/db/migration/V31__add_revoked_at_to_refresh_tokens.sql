-- Add revoked_at timestamp to refresh_tokens for grace period on replay detection
ALTER TABLE refresh_tokens ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE;

