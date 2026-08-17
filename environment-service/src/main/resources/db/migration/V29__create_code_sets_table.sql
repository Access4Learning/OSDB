-- V29__create_code_sets_table.sql

CREATE TABLE code_sets (
    uuid             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code_set_id      VARCHAR(128) NOT NULL,
    zone             VARCHAR(255) NOT NULL,
    version          VARCHAR(255) NOT NULL,
    timestamp        VARCHAR(255) NOT NULL,
    source           VARCHAR(2048),
    code_items       JSONB,
    solution_uuid    UUID         NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_code_sets_solution
        FOREIGN KEY (solution_uuid)
        REFERENCES solutions(id)
        ON DELETE CASCADE
);

-- Unique constraint: no duplicate (code_set_id, zone) within a solution
CREATE UNIQUE INDEX uk_code_sets_id_zone_solution
    ON code_sets(code_set_id, zone, solution_uuid);

-- Index for zone-scoped queries
CREATE INDEX idx_code_sets_zone ON code_sets(zone);

-- Index for solution-scoped queries
CREATE INDEX idx_code_sets_solution_uuid ON code_sets(solution_uuid);
