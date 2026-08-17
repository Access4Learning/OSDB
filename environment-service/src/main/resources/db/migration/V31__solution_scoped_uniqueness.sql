-- V31: Fix overly-restrictive uniqueness constraints for multi-tenant support
--
-- applicationName (consumerName in SIF) is a descriptive label per the SIF spec.
-- The SIF 409 rule applies to applicationKey, not consumerName.
-- Drop the global UNIQUE constraint entirely — no replacement needed.
--
-- Provider registration uniqueness is scoped to environment, not global.
-- Two environments should each be able to provide the same service/zone/context.

-- 1. Drop global UNIQUE on application_name (was auto-generated in V1)
--    applicationName is descriptive only; no uniqueness constraint needed.
ALTER TABLE environments DROP CONSTRAINT environments_application_name_key;

-- 2. Drop global UNIQUE on provider service/zone/context tuple (named in V4)
ALTER TABLE provider_registry DROP CONSTRAINT uk_provider_service;

-- 3. Add environment-scoped unique: provider tuple unique within an environment
ALTER TABLE provider_registry ADD CONSTRAINT uk_provider_env_service UNIQUE (environment_id, service_name, zone_id, context_id);
