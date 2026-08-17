-- Migration: V14__create_entity_tags_table.sql
-- Description: Creates the entity_tags table for solution-scoped polymorphic tagging

CREATE TABLE entity_tags (
    id UUID PRIMARY KEY,
    solution_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    tag_key VARCHAR(255) NOT NULL,
    tag_value VARCHAR(1024),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_entity_tags_solution FOREIGN KEY (solution_id)
        REFERENCES solutions(id) ON DELETE CASCADE
);

ALTER TABLE entity_tags
    ADD CONSTRAINT uk_entity_tags_unique
    UNIQUE (solution_id, entity_type, entity_id, tag_key);

CREATE INDEX idx_entity_tags_lookup
    ON entity_tags (solution_id, entity_type, entity_id);

CREATE INDEX idx_entity_tags_by_key
    ON entity_tags (solution_id, tag_key);
