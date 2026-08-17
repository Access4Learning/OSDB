-- Migration: V8__add_solution_id_to_zones.sql
-- Description: Adds solution_id column to zones table and updates constraints
-- Requirements: 3.1, 3.2, 3.5, 5.5, 5.8

-- Add solution_id column (nullable for backward compatibility with existing data)
ALTER TABLE zones ADD COLUMN solution_id VARCHAR(255);

-- Note: Referential integrity for solution_id is enforced at the application layer
-- (ZoneService validates solution existence before zone creation)
-- A database-level FK is not used because solution_id is not globally unique
-- (the same solution_id can exist in different domains)

-- Drop the foreign key constraint on contexts that depends on zones_zone_id_key first
ALTER TABLE contexts DROP CONSTRAINT IF EXISTS fk_contexts_zone;

-- Drop existing unique constraint on zone_id
ALTER TABLE zones DROP CONSTRAINT IF EXISTS zones_zone_id_key;

-- Add composite unique constraint on (solution_id, zone_id)
-- This allows the same zone_id to exist in different solutions
ALTER TABLE zones ADD CONSTRAINT uk_solution_zone UNIQUE (solution_id, zone_id);

-- Add index on solution_id for fast lookups by solution
CREATE INDEX idx_zones_solution_id ON zones(solution_id);
