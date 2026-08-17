-- Create zones and contexts tables
-- These tables manage logical groupings of services and data within the broker
-- Requirements: 9.2, 9.3

-- Create zones table
-- Zones provide logical grouping of services and data within the broker
CREATE TABLE zones (
    id UUID PRIMARY KEY,
    zone_id VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL
);

-- Create unique index on zone_id (will be replaced by composite unique in V8)
ALTER TABLE zones ADD CONSTRAINT zones_zone_id_key UNIQUE (zone_id);

-- Create index on zone_id for fast lookups
CREATE INDEX idx_zones_zone_id ON zones(zone_id);

-- Create contexts table
-- Contexts provide additional routing dimension for message delivery within a zone
CREATE TABLE contexts (
    id UUID PRIMARY KEY,
    zone_id VARCHAR(255) NOT NULL,
    context_id VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_contexts_zone FOREIGN KEY (zone_id) REFERENCES zones(zone_id) ON DELETE CASCADE,
    CONSTRAINT uk_contexts_zone_context UNIQUE (zone_id, context_id)
);

-- Create index on zone_id for fast lookups by zone
CREATE INDEX idx_contexts_zone_id ON contexts(zone_id);