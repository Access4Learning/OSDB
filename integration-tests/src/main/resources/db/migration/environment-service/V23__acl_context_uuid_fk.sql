-- Migration: V23__acl_context_uuid_fk.sql
-- Description: Replaces the loose zone_id/context_id VARCHAR columns on acl_entries with a single
--              context_uuid UUID foreign key referencing contexts(id). Null context_uuid preserves
--              the existing wildcard semantics (null = applies to entire zone).
--              Orphaned ACL entries referencing non-existent zone/context combinations are deleted.
-- Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9

-- Step 1: Add nullable context_uuid column
ALTER TABLE acl_entries ADD COLUMN context_uuid UUID;

-- Step 2: Populate context_uuid by joining zone_id/context_id to contexts
UPDATE acl_entries ae
SET context_uuid = c.id
FROM contexts c
JOIN zones z ON c.zone_uuid = z.id
WHERE ae.zone_id = z.zone_id
  AND ae.context_id = c.context_id;

-- Step 3: Delete orphaned rows (non-null context_id that didn't resolve)
DELETE FROM acl_entries
WHERE context_id IS NOT NULL AND context_uuid IS NULL;

-- Step 4: Add FK constraint
ALTER TABLE acl_entries
  ADD CONSTRAINT fk_acl_context_uuid
  FOREIGN KEY (context_uuid) REFERENCES contexts(id) ON DELETE CASCADE;

-- Step 5: Drop old unique constraint and index
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS uk_acl_entry;
DROP INDEX IF EXISTS idx_acl_lookup;

-- Step 6: Drop old columns
ALTER TABLE acl_entries DROP COLUMN zone_id;
ALTER TABLE acl_entries DROP COLUMN context_id;

-- Step 7: Add new unique constraint and index
ALTER TABLE acl_entries
  ADD CONSTRAINT uk_acl_entry UNIQUE (environment_id, service_name, context_uuid, access_type);

CREATE INDEX idx_acl_lookup
  ON acl_entries(environment_id, service_name, context_uuid, access_type);
