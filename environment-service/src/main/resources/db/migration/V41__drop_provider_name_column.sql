-- V41: Remove provider_name column from provider_registry
-- Requirements: 1.1, 11.1
ALTER TABLE provider_registry DROP COLUMN provider_name;
