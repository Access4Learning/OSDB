-- Migration: V22__bind_subscriptions_to_acl_entries.sql
-- Description: Binds subscriptions to ACL entries via a foreign key (acl_entry_id).
--              Replaces the loose service_name, zone_id, context_id columns with a single
--              acl_entry_id UUID FK referencing acl_entries(id) with ON DELETE CASCADE.
--              Orphaned subscriptions with no matching ACL entry are deleted.
-- Requirements: 1.1–1.6
--
-- This migration is atomic: if any step fails, PostgreSQL rolls back the entire transaction.
-- The NOT NULL constraint after data population serves as a safety check for orphaned rows.

-- ============================================================
-- 1. Add nullable acl_entry_id UUID column
-- ============================================================
ALTER TABLE subscriptions ADD COLUMN acl_entry_id UUID;

-- ============================================================
-- 2. Populate acl_entry_id by matching service_name, zone_id, context_id
--    to acl_entries rows using the access type mapping:
--      EVENT           → SUBSCRIBE
--      DELAYED_REQUEST → PROVIDE
-- ============================================================
UPDATE subscriptions s
SET acl_entry_id = a.id
FROM acl_entries a
WHERE s.service_name = a.service_name
  AND s.zone_id = a.zone_id
  AND ((s.context_id IS NULL AND a.context_id IS NULL) OR s.context_id = a.context_id)
  AND (
        (s.type = 'EVENT' AND a.access_type = 'SUBSCRIBE')
     OR (s.type = 'DELAYED_REQUEST' AND a.access_type = 'PROVIDE')
  );

-- ============================================================
-- 3. Delete orphaned subscriptions with no matching ACL entry
-- ============================================================
DELETE FROM subscriptions WHERE acl_entry_id IS NULL;

-- ============================================================
-- 4. Set acl_entry_id to NOT NULL
-- ============================================================
ALTER TABLE subscriptions ALTER COLUMN acl_entry_id SET NOT NULL;

-- ============================================================
-- 5. Add FK constraint with ON DELETE CASCADE
-- ============================================================
ALTER TABLE subscriptions ADD CONSTRAINT fk_subscriptions_acl_entry
    FOREIGN KEY (acl_entry_id) REFERENCES acl_entries(id) ON DELETE CASCADE;

-- ============================================================
-- 6. Drop old columns (service_name, zone_id, context_id)
-- ============================================================
ALTER TABLE subscriptions DROP COLUMN service_name;
ALTER TABLE subscriptions DROP COLUMN zone_id;
ALTER TABLE subscriptions DROP COLUMN context_id;

-- ============================================================
-- 7. Drop old unique constraint and add new one
-- ============================================================
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS uq_subscription;
ALTER TABLE subscriptions ADD CONSTRAINT uq_subscription_acl UNIQUE (acl_entry_id, queue_id, type);

-- ============================================================
-- 8. Drop old index and add new one
-- ============================================================
DROP INDEX IF EXISTS idx_service_zone_context_type;
CREATE INDEX idx_subscriptions_acl_entry_id ON subscriptions(acl_entry_id);

-- Note: idx_queue_id is preserved (created in V18, still valid on queue_id column)
