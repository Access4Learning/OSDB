-- Add foreign key constraint from queues.owner_id to environments.id
-- This ensures queues are cascade-deleted when their owning environment is deleted.
-- Also cleans up any orphaned queues (owner_id referencing non-existent environments)
-- before adding the constraint.

-- 1. Delete orphaned queues whose owner_id doesn't exist in environments
DELETE FROM queues WHERE owner_id NOT IN (SELECT id FROM environments);

-- 2. Add the foreign key constraint with cascade delete (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'fk_queues_owner'
                     AND table_name = 'queues') THEN
        ALTER TABLE queues
        ADD CONSTRAINT fk_queues_owner
        FOREIGN KEY (owner_id) REFERENCES environments(id) ON DELETE CASCADE;
    END IF;
END $$;
