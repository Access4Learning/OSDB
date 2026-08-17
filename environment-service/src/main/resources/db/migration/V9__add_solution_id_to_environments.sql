-- Migration: V9__add_solution_id_to_environments.sql
-- Description: Adds solution_id column to environments table
-- Requirements: 4.1, 4.2, 5.6, 5.8

-- Add solution_id column (nullable for SIF 3.3 compatibility)
ALTER TABLE environments ADD COLUMN solution_id VARCHAR(255);

-- Note: Referential integrity for solution_id is enforced at the application layer
-- (EnvironmentService validates solution existence before environment registration)
-- A database-level FK is not used because solution_id is not globally unique
-- (the same solution_id can exist in different domains)

-- Add index on solution_id for fast lookups by solution
CREATE INDEX idx_environments_solution_id ON environments(solution_id);
