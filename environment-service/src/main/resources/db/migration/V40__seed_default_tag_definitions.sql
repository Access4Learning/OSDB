-- Migration: V40__seed_default_tag_definitions.sql
-- Description: Seeds default tag definitions for core broker features

INSERT INTO tag_definitions (id, tag_name, tag_description, applicable_entity_types, allowed_values, created_by, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'SEQUENCING_ENABLED',
    'Enables monotonically increasing x-sif-sequenceId header injection on messages within the solution. The broker generates a Redis-backed atomic sequence number for each inbound message, providing consumers with a total ordering guarantee within the domain/solution scope.',
    ARRAY['SOLUTION'],
    ARRAY['true', 'false'],
    'system',
    now(),
    now()
)
ON CONFLICT (UPPER(tag_name)) DO NOTHING;

INSERT INTO tag_definitions (id, tag_name, tag_description, applicable_entity_types, allowed_values, created_by, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'SIF_LEGACY_MATRIX_PARAMS',
    'Indicates the environment expects SIF 3.3 legacy matrix parameter formatting in the relativeServicePath header. When enabled, the requests-connector and responses-connector format outbound relativeServicePath as {serviceName};contextId={contextId};zoneId={zoneId}. When absent or false, modern headers-only format is used.',
    ARRAY['ENVIRONMENT'],
    ARRAY['true', 'false'],
    'system',
    now(),
    now()
)
ON CONFLICT (UPPER(tag_name)) DO NOTHING;
