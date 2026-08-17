-- Create job_phases table
-- This table stores job phase metadata for the Job Service
-- Consolidated from job-service/src/main/resources/db/migration/V2__create_job_phases_table.sql
-- Uses IF NOT EXISTS to support idempotent application on databases where the table already exists

CREATE TABLE IF NOT EXISTS job_phases (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    phase_name VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    metadata JSONB,

    CONSTRAINT fk_phase_job FOREIGN KEY (job_id)
        REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT chk_phase_state CHECK (state IN ('NOTSTARTED', 'INPROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT uk_job_phase UNIQUE (job_id, phase_name)
);

-- Create indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_phases_job ON job_phases(job_id);
CREATE INDEX IF NOT EXISTS idx_phases_created ON job_phases(created_at);
CREATE INDEX IF NOT EXISTS idx_phases_state ON job_phases(state);
