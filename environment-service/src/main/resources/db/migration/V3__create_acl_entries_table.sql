-- Create acl_entries table
-- This table stores access control list entries defining what services an environment can access
-- Requirements: 9.4

-- Create acl_entries table
-- ACL entries define permissions for environments to access specific services
CREATE TABLE acl_entries (
    id UUID PRIMARY KEY,
    environment_id UUID NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    zone_id VARCHAR(255) NOT NULL,
    context_id VARCHAR(255),
    access_type VARCHAR(50) NOT NULL,
    access_level VARCHAR(50) NOT NULL,
    CONSTRAINT fk_acl_environment FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT chk_access_type CHECK (access_type IN ('PROVIDE', 'CONSUME', 'SUBSCRIBE')),
    CONSTRAINT chk_access_level CHECK (access_level IN ('APPROVED', 'REJECTED', 'PENDING')),
    CONSTRAINT uk_acl_entry UNIQUE (environment_id, service_name, zone_id, context_id, access_type)
);

-- Create indexes for performance optimization
-- Index on environment_id for fast lookups of ACL entries by environment
CREATE INDEX idx_acl_environment_id ON acl_entries(environment_id);

-- Composite index for fast permission lookups during authorization checks
-- This index supports the exact matching required for ACL permission validation
CREATE INDEX idx_acl_lookup ON acl_entries(environment_id, service_name, zone_id, context_id, access_type);