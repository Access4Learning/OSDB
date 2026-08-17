-- V27: Align provider_registry table with SIF 3.7 specification
-- Adds service_type, provider_name columns; replaces supported_patterns with supported_request_types JSONB
-- Requirements: 1.5, 1.6, 2.7, 2.8, 3.5, 3.6, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7

-- 1. Add service_type column with default for existing rows
ALTER TABLE provider_registry ADD COLUMN service_type VARCHAR(50);
UPDATE provider_registry SET service_type = 'OBJECT' WHERE service_type IS NULL;
ALTER TABLE provider_registry ALTER COLUMN service_type SET NOT NULL;

-- 2. Add provider_name column with default for existing rows
ALTER TABLE provider_registry ADD COLUMN provider_name VARCHAR(255);
UPDATE provider_registry SET provider_name = 'Unknown' WHERE provider_name IS NULL;
ALTER TABLE provider_registry ALTER COLUMN provider_name SET NOT NULL;

-- 3. Add supported_request_types JSONB column, migrate data from supported_patterns
ALTER TABLE provider_registry ADD COLUMN supported_request_types JSONB;

-- Migrate: each comma-separated pattern becomes a requestType entry with all 4 operations
UPDATE provider_registry SET supported_request_types = (
    SELECT jsonb_agg(
        jsonb_build_object(
            'requestType', pattern,
            'operations', '["CREATE","QUERY","UPDATE","DELETE"]'::jsonb
        )
    )
    FROM unnest(string_to_array(supported_patterns, ',')) AS pattern
)
WHERE supported_request_types IS NULL;

-- Handle edge case: if supported_patterns was empty, set a default
UPDATE provider_registry
SET supported_request_types = '[{"requestType":"IMMEDIATE","operations":["CREATE","QUERY","UPDATE","DELETE"]}]'::jsonb
WHERE supported_request_types IS NULL;

ALTER TABLE provider_registry ALTER COLUMN supported_request_types SET NOT NULL;

-- 4. Drop old column
ALTER TABLE provider_registry DROP COLUMN supported_patterns;
