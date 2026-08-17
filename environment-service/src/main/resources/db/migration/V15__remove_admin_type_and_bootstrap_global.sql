-- Migration: V15__remove_admin_type_and_bootstrap_global.sql
-- Description: Removes ADMIN environment type, bootstraps global domain/solution,
--              converts ADMIN environments to BROKERED, and makes solution_id NOT NULL.

-- 1. Ensure global domain exists
INSERT INTO domains (id, domain_id, description, created_at)
VALUES (gen_random_uuid(), 'GLOBAL_DOMAIN', 'Global admin domain', NOW())
ON CONFLICT (domain_id) DO NOTHING;

-- 2. Ensure global solution exists
INSERT INTO solutions (id, domain_id, solution_id, description, created_at)
VALUES (gen_random_uuid(), 'GLOBAL_DOMAIN', 'GLOBAL_SOLUTION', 'Global admin solution', NOW())
ON CONFLICT (domain_id, solution_id) DO NOTHING;

-- 3. Convert existing ADMIN environments to BROKERED with global solution
UPDATE environments SET type = 'BROKERED', solution_id = 'GLOBAL_SOLUTION'
  WHERE type = 'ADMIN';

-- 4. Set default solution_id for any null rows
UPDATE environments SET solution_id = 'unknown' WHERE solution_id IS NULL;

-- 5. Make solution_id NOT NULL
ALTER TABLE environments ALTER COLUMN solution_id SET NOT NULL;

-- 6. Update type constraint to only allow DIRECT/BROKERED
ALTER TABLE environments DROP CONSTRAINT IF EXISTS chk_type;
ALTER TABLE environments ADD CONSTRAINT chk_type CHECK (type IN ('DIRECT', 'BROKERED'));
