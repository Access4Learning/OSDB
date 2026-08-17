-- Create provision_requests table
-- This table stores consumer self-provisioning requests for additional ACL rights
-- on services within specific zones and contexts (SIF 3.7 Infrastructure Services section 6)
-- Requirements: 8.1, 8.2, 8.3

CREATE TABLE provision_requests (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id            UUID NOT NULL,
    completion_status         VARCHAR(20),
    provisioned_zones_payload JSONB NOT NULL,
    created_at                TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at               TIMESTAMP,
    CONSTRAINT fk_provision_requests_environment
        FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Index on environment_id for fast lookups of provision requests by environment
CREATE INDEX idx_provision_requests_environment_id ON provision_requests(environment_id);
