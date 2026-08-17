-- V32: Enforce single provider per service per zone/context
--
-- Per SIF 3.7, only one environment may PROVIDE a given service within a zone/context.
-- This partial unique index enforces that constraint at the database level.
-- It only applies to rows where access_type = 'PROVIDE'.

CREATE UNIQUE INDEX uk_acl_single_provider
    ON acl_entries (service_name, context_uuid)
    WHERE access_type = 'PROVIDE';
