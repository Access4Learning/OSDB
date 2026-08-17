-- V36__create_admin_users_table.sql
-- Description: Creates the admin_users table for human operator accounts in the admin console.
--              Admin users authenticate with username/password (bcrypt) and are scoped to a
--              domain+solution combination.
-- Requirements: 8.1, 8.3, 8.5, 8.6, 8.7

CREATE TABLE admin_users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    domain_uuid   UUID         NOT NULL,
    solution_uuid UUID         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,

    CONSTRAINT fk_admin_users_domain
        FOREIGN KEY (domain_uuid)
        REFERENCES domains(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_admin_users_solution
        FOREIGN KEY (solution_uuid)
        REFERENCES solutions(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_admin_users_username_scope
    ON admin_users(username, domain_uuid, solution_uuid);

CREATE INDEX idx_admin_users_domain_uuid ON admin_users(domain_uuid);
CREATE INDEX idx_admin_users_solution_uuid ON admin_users(solution_uuid);
