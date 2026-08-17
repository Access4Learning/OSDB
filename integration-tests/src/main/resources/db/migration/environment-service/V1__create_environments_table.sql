-- Create environments table
-- This table stores application registrations with their credentials and configuration
-- Requirements: 9.1

CREATE TABLE environments (
    id UUID PRIMARY KEY,
    application_name VARCHAR(255) NOT NULL UNIQUE,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    shared_secret VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    default_zone_id VARCHAR(255) NOT NULL,
    default_context_id VARCHAR(255),
    type VARCHAR(50) NOT NULL,
    CONSTRAINT chk_type CHECK (type IN ('CONSUMER', 'PROVIDER', 'BROKER'))
);

-- Create indexes for performance optimization
-- Index on session_token for fast authentication lookups
CREATE INDEX idx_environments_session_token ON environments(session_token);

-- Index on type for filtering environments by type
CREATE INDEX idx_environments_type ON environments(type);