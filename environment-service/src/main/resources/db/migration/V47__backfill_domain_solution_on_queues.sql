-- Backfill domain_id and solution_id for queues created before V46 added these columns.
-- Derives values by joining through the solution_uuid FK to the solutions table,
-- then through solutions.domain_uuid to the domains table for the string domain_id.
-- (V21 dropped solutions.domain_id and replaced it with domain_uuid UUID FK.)
UPDATE queues q
SET domain_id = d.domain_id,
    solution_id = s.solution_id
FROM solutions s
JOIN domains d ON s.domain_uuid = d.id
WHERE q.solution_uuid = s.id
  AND (q.domain_id IS NULL OR q.solution_id IS NULL);

-- Note: Rows with solution_uuid IS NULL are legacy orphans that cannot be
-- backfilled via join. These should be investigated and cleaned up manually.
