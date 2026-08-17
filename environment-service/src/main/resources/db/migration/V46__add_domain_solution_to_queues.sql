-- Add domainId and solutionId string columns to queues table.
-- These are the URL path-segment identifiers used to construct Redis stream keys.
-- The QueueDepthGaugeUpdater needs them to resolve the correct stream key
-- without relying on request context.

ALTER TABLE queues ADD COLUMN IF NOT EXISTS domain_id VARCHAR(255);
ALTER TABLE queues ADD COLUMN IF NOT EXISTS solution_id VARCHAR(255);

-- Index for potential future queries by domain/solution
CREATE INDEX IF NOT EXISTS idx_queues_domain_solution ON queues(domain_id, solution_id);

COMMENT ON COLUMN queues.domain_id IS 'Domain path identifier used in Redis stream key construction';
COMMENT ON COLUMN queues.solution_id IS 'Solution path identifier used in Redis stream key construction';
