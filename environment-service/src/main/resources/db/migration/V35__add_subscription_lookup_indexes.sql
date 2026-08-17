-- V35: Add composite indexes to accelerate subscription fan-out lookups
--
-- The subscription lookup query joins subscriptions → acl_entries → context_services
-- and filters on context_services.service_name, context_services.context_uuid,
-- and subscriptions.type. These indexes cover the hot path for event distribution.
--
-- 1. context_services(service_name, context_uuid) — covers the WHERE clause filter
--    on the context_services table. The existing uk_context_service unique constraint
--    is (context_uuid, service_name) which is suboptimal when service_name is the
--    leading filter column.
--
-- 2. subscriptions(acl_entry_id, type) — covers the JOIN from acl_entries and the
--    type filter in a single index scan. The existing idx_subscriptions_acl_entry_id
--    only covers acl_entry_id.

CREATE INDEX IF NOT EXISTS idx_context_services_service_name_context_uuid
    ON context_services (service_name, context_uuid);

CREATE INDEX IF NOT EXISTS idx_subscriptions_acl_entry_id_type
    ON subscriptions (acl_entry_id, type);
