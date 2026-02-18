-- Add unique constraint on FCM token to prevent duplicate token registrations
-- First, remove any duplicate tokens (keep the most recently updated one)
DELETE FROM fcm_tokens a USING fcm_tokens b
WHERE a.id < b.id AND a.token = b.token;

-- Add unique constraint on token column
ALTER TABLE fcm_tokens ADD CONSTRAINT uq_fcm_tokens_token UNIQUE (token);

-- Also add unique constraint on device_registration_id to ensure one token per device
-- First, remove any duplicates (keep the most recently updated one)
DELETE FROM fcm_tokens a USING fcm_tokens b
WHERE a.id < b.id AND a.device_registration_id = b.device_registration_id;

ALTER TABLE fcm_tokens ADD CONSTRAINT uq_fcm_tokens_device_registration UNIQUE (device_registration_id);

