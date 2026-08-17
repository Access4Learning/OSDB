-- Add ADMIN environment type to the check constraint
-- This migration updates the type check constraint to include ADMIN
-- Requirements: 1.1, 1.2

-- Drop the old check constraint
ALTER TABLE environments DROP CONSTRAINT chk_type;

-- Add the new check constraint with ADMIN included
ALTER TABLE environments ADD CONSTRAINT chk_type CHECK (type IN ('CONSUMER', 'PROVIDER', 'BROKER', 'ADMIN'));
