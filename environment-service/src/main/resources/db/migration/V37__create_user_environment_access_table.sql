-- Migration: V37__create_user_environment_access_table.sql
-- Description: Creates the user_environment_access join table linking admin users to environments
-- Requirements: 8.2, 8.4, 8.5, 8.6, 8.7

CREATE TABLE user_environment_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_env_access_user FOREIGN KEY (user_id)
        REFERENCES admin_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_env_access_environment FOREIGN KEY (environment_id)
        REFERENCES environments(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_user_environment_access
    ON user_environment_access(user_id, environment_id);
CREATE INDEX idx_user_env_access_user_id ON user_environment_access(user_id);
CREATE INDEX idx_user_env_access_environment_id ON user_environment_access(environment_id);
