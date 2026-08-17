-- V33: Create context_services table and refactor acl_entries/provider_registry
--
-- Introduces a context_services table normalizing service-context relationships.
-- acl_entries and provider_registry now reference context_services via FK instead of
-- storing loose service_name/context_uuid/zone_id/context_id columns.
-- No production data to preserve — tables are truncated before restructuring.
--
-- Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2

-- 1. Truncate dependent tables (no data to preserve)
TRUNCATE acl_entries, provider_registry CASCADE;

-- 2. Drop old constraints and indexes on acl_entries
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS uk_acl_entry;
DROP INDEX IF EXISTS idx_acl_lookup;
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS fk_acl_context_uuid;
DROP INDEX IF EXISTS uk_acl_single_provider;

-- 3. Drop old columns from acl_entries
ALTER TABLE acl_entries DROP COLUMN service_name;
ALTER TABLE acl_entries DROP COLUMN context_uuid;

-- 4. Drop old constraints and indexes on provider_registry
ALTER TABLE provider_registry DROP CONSTRAINT IF EXISTS uk_provider_env_service;
DROP INDEX IF EXISTS idx_provider_lookup;

-- 5. Drop old columns from provider_registry
ALTER TABLE provider_registry DROP COLUMN service_name;
ALTER TABLE provider_registry DROP COLUMN zone_id;
ALTER TABLE provider_registry DROP COLUMN context_id;

-- 6. Create context_services table
CREATE TABLE context_services (
    id UUID PRIMARY KEY,
    context_uuid UUID NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_context_services_context
        FOREIGN KEY (context_uuid) REFERENCES contexts(id) ON DELETE CASCADE,
    CONSTRAINT uk_context_service
        UNIQUE (context_uuid, service_name),
    CONSTRAINT chk_context_service_type
        CHECK (service_type IN ('UTILITY', 'OBJECT', 'FUNCTIONAL', 'SERVICEPATH', 'XQUERYTEMPLATE', 'SERVICE'))
);

CREATE INDEX idx_context_services_context_uuid ON context_services(context_uuid);

-- 7. Add context_service_uuid FK to acl_entries
ALTER TABLE acl_entries ADD COLUMN context_service_uuid UUID NOT NULL;

ALTER TABLE acl_entries ADD CONSTRAINT fk_acl_context_service
    FOREIGN KEY (context_service_uuid) REFERENCES context_services(id) ON DELETE CASCADE;

ALTER TABLE acl_entries ADD CONSTRAINT uk_acl_entry
    UNIQUE (environment_id, context_service_uuid, access_type);

CREATE UNIQUE INDEX uk_acl_single_provider
    ON acl_entries (context_service_uuid) WHERE access_type = 'PROVIDE';

-- 8. Add context_service_uuid FK to provider_registry
ALTER TABLE provider_registry ADD COLUMN context_service_uuid UUID NOT NULL;

ALTER TABLE provider_registry ADD CONSTRAINT fk_provider_context_service
    FOREIGN KEY (context_service_uuid) REFERENCES context_services(id) ON DELETE CASCADE;

ALTER TABLE provider_registry ADD CONSTRAINT uk_provider_context_service
    UNIQUE (environment_id, context_service_uuid);
