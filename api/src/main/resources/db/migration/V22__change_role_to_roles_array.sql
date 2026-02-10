-- Migration: Change role column to roles array in refresh_tokens table
-- Converts single role VARCHAR(50) to PostgreSQL TEXT[] array to support multiple roles per token

-- Add new roles column as array
ALTER TABLE refresh_tokens 
ADD COLUMN roles TEXT[];

-- Migrate existing data: convert single role string to array
UPDATE refresh_tokens 
SET roles = ARRAY[TRIM(role)]::TEXT[] 
WHERE role IS NOT NULL AND role != '';

-- Handle null roles
UPDATE refresh_tokens 
SET roles = '{}'::TEXT[] 
WHERE role IS NULL OR role = '';

-- Drop the old single role column
ALTER TABLE refresh_tokens 
DROP COLUMN role;

-- Add index on roles for query optimization
CREATE INDEX idx_refresh_tokens_roles ON refresh_tokens USING GIN (roles);
