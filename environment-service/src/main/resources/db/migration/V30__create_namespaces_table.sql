-- V30__create_namespaces_table.sql

CREATE TABLE namespaces (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    zone             VARCHAR(255) NOT NULL,
    uri              VARCHAR(2048) NOT NULL,
    url              VARCHAR(2048) NOT NULL,
    solution_uuid    UUID         NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_namespaces_solution
        FOREIGN KEY (solution_uuid)
        REFERENCES solutions(id)
        ON DELETE CASCADE
);

-- Unique constraint: no duplicate (uri, zone) within a solution
CREATE UNIQUE INDEX uk_namespaces_uri_zone_solution
    ON namespaces(uri, zone, solution_uuid);

-- Index for zone-scoped queries
CREATE INDEX idx_namespaces_zone ON namespaces(zone);

-- Index for solution-scoped queries
CREATE INDEX idx_namespaces_solution_uuid ON namespaces(solution_uuid);
