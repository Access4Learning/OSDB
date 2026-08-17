-- Create provider_registry table
-- This table stores provider service registrations and their capabilities
-- Requirements: 9.5

-- Create provider_registry table
-- Provider registrations track service offerings and their capabilities
CREATE TABLE provider_registry (
    id UUID PRIMARY KEY,
    environment_id UUID NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    zone_id VARCHAR(255) NOT NULL,
    context_id VARCHAR(255),
    endpoint VARCHAR(1024) NOT NULL,
    supported_patterns VARCHAR(255) NOT NULL, -- Comma-separated: IMMEDIATE,DELAYED,EVENT
    registered_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_provider_environment FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT uk_provider_service UNIQUE (service_name, zone_id, context_id)
);

-- Create indexes for performance optimization
-- Index on environment_id for fast lookups of provider registrations by environment
CREATE INDEX idx_provider_environment_id ON provider_registry(environment_id);

-- Composite index for fast provider lookups during service discovery
-- This index supports lookups by service name, zone, and context combination
CREATE INDEX idx_provider_lookup ON provider_registry(service_name, zone_id, context_id);