-- Create solution_services table
-- Service catalog per solution: defines which service name + type combinations
-- are available for administrators when provisioning zones, creating ACLs, etc.
-- Mirrors legacy schema's solutionServices table.

CREATE TABLE solution_services (
    id UUID PRIMARY KEY,
    solution_id UUID NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_solution_services_solution
        FOREIGN KEY (solution_id) REFERENCES solutions(id) ON DELETE CASCADE,
    CONSTRAINT uk_solution_service_name_type
        UNIQUE (solution_id, service_name, service_type),
    CONSTRAINT chk_service_type
        CHECK (service_type IN ('UTILITY', 'OBJECT', 'FUNCTIONAL', 'SERVICEPATH', 'XQUERYTEMPLATE', 'SERVICE'))
);

CREATE INDEX idx_solution_services_solution_id ON solution_services(solution_id);
