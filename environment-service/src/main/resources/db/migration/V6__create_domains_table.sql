-- Migration: V6__create_domains_table.sql
-- Description: Creates the domains table for multi-tenant client isolation
-- Requirements: 5.1, 5.2, 7.1, 7.3, 7.5, 7.7

CREATE TABLE domains (
    id UUID PRIMARY KEY,
    domain_id VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_domains_domain_id ON domains(domain_id);
