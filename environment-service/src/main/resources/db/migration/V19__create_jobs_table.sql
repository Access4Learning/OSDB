-- Create jobs table
-- This table stores job metadata for the Job Service
-- Consolidated from job-service/src/main/resources/db/migration/V1__create_jobs_table.sql
-- Uses IF NOT EXISTS to support idempotent application on databases where the table already exists

CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY,
    environment_id UUID NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    zone_id VARCHAR(255) NOT NULL,
    context_id VARCHAR(255),
    state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    current_phase VARCHAR(100),
    metadata JSONB,
    version INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_state CHECK (state IN ('NOTSTARTED', 'INPROGRESS', 'COMPLETED', 'FAILED'))
);

-- Create indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_jobs_environment ON jobs(environment_id);
CREATE INDEX IF NOT EXISTS idx_jobs_state ON jobs(state);
CREATE INDEX IF NOT EXISTS idx_jobs_service ON jobs(service_name);
CREATE INDEX IF NOT EXISTS idx_jobs_created ON jobs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_env_state ON jobs(environment_id, state);
CREATE INDEX IF NOT EXISTS idx_jobs_env_service ON jobs(environment_id, service_name);
