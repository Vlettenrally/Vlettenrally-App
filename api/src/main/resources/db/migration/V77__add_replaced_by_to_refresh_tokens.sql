-- Track the successor token issued when a refresh token is rotated.
-- Enables idempotent replay: a client that lost the refresh response and retries
-- the old token can be handed back the successor pair instead of triggering a
-- family-wide revocation (which would lock the whole team out).
ALTER TABLE refresh_tokens ADD COLUMN replaced_by_token TEXT;
