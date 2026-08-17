-- V43__add_target_environment_id_to_alerts.sql

ALTER TABLE alerts
    ADD COLUMN target_environment_id UUID;

ALTER TABLE alerts
    ADD CONSTRAINT fk_alerts_target_environment
        FOREIGN KEY (target_environment_id)
        REFERENCES environments(id)
        ON DELETE SET NULL;

CREATE INDEX idx_alerts_target_environment_id ON alerts(target_environment_id);
