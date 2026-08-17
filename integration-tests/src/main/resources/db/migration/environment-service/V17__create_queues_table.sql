-- Create queues table
-- This table stores queue metadata for the Queue Service
-- Consolidated from queue-service/src/main/resources/db/migration/V1__create_queues_table.sql
-- Uses IF NOT EXISTS to support idempotent application on databases where the table already exists

CREATE TABLE IF NOT EXISTS queues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    max_retries INTEGER NOT NULL DEFAULT 3,
    retry_delay_ms BIGINT NOT NULL DEFAULT 5000,

    CONSTRAINT queues_max_retries_positive CHECK (max_retries >= 0),
    CONSTRAINT queues_retry_delay_positive CHECK (retry_delay_ms >= 0)
);

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_queues_owner_id ON queues(owner_id);
CREATE INDEX IF NOT EXISTS idx_queues_name ON queues(name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_queues_owner_name ON queues(owner_id, name);

-- Add comments for documentation
COMMENT ON TABLE queues IS 'Message queues for asynchronous message delivery';
COMMENT ON COLUMN queues.id IS 'Unique identifier for the queue';
COMMENT ON COLUMN queues.name IS 'Name of the queue';
COMMENT ON COLUMN queues.owner_id IS 'ID of the environment that owns this queue';
COMMENT ON COLUMN queues.created_at IS 'Timestamp when the queue was created';
COMMENT ON COLUMN queues.max_retries IS 'Maximum number of delivery retry attempts';
COMMENT ON COLUMN queues.retry_delay_ms IS 'Delay in milliseconds between retry attempts';
