-- Migration: V39__create_tag_definitions_table.sql
-- Description: Creates the tag_definitions table for globally-managed tag templates

CREATE TABLE tag_definitions (
    id UUID PRIMARY KEY,
    tag_name VARCHAR(255) NOT NULL,
    tag_description VARCHAR(1024),
    applicable_entity_types VARCHAR[] NOT NULL,
    allowed_values VARCHAR[],
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_tag_definitions_name ON tag_definitions (UPPER(tag_name));
