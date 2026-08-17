-- V28__create_alerts_table.sql

CREATE TABLE alerts (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id    UUID         NOT NULL,
    reporter          VARCHAR(255) NOT NULL,
    cause             VARCHAR(255),
    exchange          VARCHAR(20)  NOT NULL,
    level             VARCHAR(20)  NOT NULL,
    description       TEXT,
    message_id        VARCHAR(255),
    body              TEXT,
    error             TEXT,
    xpath             VARCHAR(512),
    category          INTEGER,
    code              INTEGER,
    internal          VARCHAR(255),
    extended_elements JSONB,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_alerts_environment
        FOREIGN KEY (environment_id)
        REFERENCES environments(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_alerts_environment_id ON alerts(environment_id);
CREATE INDEX idx_alerts_message_id ON alerts(message_id);
