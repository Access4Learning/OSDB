-- Add solution_uuid column to queues table for data isolation.
-- Queues must be scoped to a solution so that getAllQueues() returns only
-- queues belonging to the requesting solution, not all queues in the database.
-- Nullable because existing rows won't be backfilled (they will be deleted manually).

ALTER TABLE queues ADD COLUMN IF NOT EXISTS solution_uuid UUID;

-- Index for efficient filtering by solution
CREATE INDEX IF NOT EXISTS idx_queues_solution_uuid ON queues(solution_uuid);

COMMENT ON COLUMN queues.solution_uuid IS 'UUID of the solution this queue belongs to (for data isolation)';
