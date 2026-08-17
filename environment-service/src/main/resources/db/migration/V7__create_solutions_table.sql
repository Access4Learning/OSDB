-- Migration: V7__create_solutions_table.sql
-- Description: Creates the solutions table for environment separation within domains
-- Requirements: 5.1, 5.3, 5.4, 7.2, 7.4, 7.5, 7.7

CREATE TABLE solutions (
    id UUID PRIMARY KEY,
    domain_id VARCHAR(255) NOT NULL,
    solution_id VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_solutions_domain FOREIGN KEY (domain_id) 
        REFERENCES domains(domain_id) ON DELETE CASCADE,
    CONSTRAINT uk_domain_solution UNIQUE (domain_id, solution_id)
);

CREATE INDEX idx_solutions_domain_id ON solutions(domain_id);
CREATE INDEX idx_solutions_solution_id ON solutions(solution_id);
