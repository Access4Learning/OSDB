CREATE TABLE xquery_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_token     VARCHAR(256) NOT NULL,
    type            VARCHAR(20) NOT NULL CHECK (type IN ('SINGULAR', 'FORMULA', 'EXTENDED')),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'DISALLOWED')),
    qualifier       VARCHAR(1024),
    description     VARCHAR(1024),
    script          TEXT,
    parameters      JSONB NOT NULL DEFAULT '[]',
    return_type     VARCHAR(512),
    environment_id  UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    solution_uuid   UUID NOT NULL REFERENCES solutions(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_xquery_token_solution UNIQUE (query_token, solution_uuid)
);

CREATE INDEX idx_xquery_templates_solution ON xquery_templates(solution_uuid);
CREATE INDEX idx_xquery_templates_token ON xquery_templates(query_token);
