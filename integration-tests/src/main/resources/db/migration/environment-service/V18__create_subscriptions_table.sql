-- Create subscriptions table
-- This table stores subscription metadata for message routing in the SIF Unity Broker
-- Consolidated from subscription-service/src/main/resources/db/migration/V1__create_subscriptions_table.sql
-- Uses IF NOT EXISTS to support idempotent application on databases where the table already exists

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id UUID NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    context_id VARCHAR(100),  -- NULL for wildcard subscriptions
    type VARCHAR(20) NOT NULL CHECK (type IN ('EVENT', 'DELAYED_REQUEST')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Uniqueness constraint: prevent duplicate subscriptions
    CONSTRAINT uq_subscription UNIQUE (queue_id, service_name, zone_id, context_id, type)
);

-- Index for fast subscription queries by service name, zone, context, and type
CREATE INDEX IF NOT EXISTS idx_service_zone_context_type
    ON subscriptions (service_name, zone_id, context_id, type);

-- Index for fast lookups of all subscriptions for a given queue
CREATE INDEX IF NOT EXISTS idx_queue_id ON subscriptions (queue_id);
