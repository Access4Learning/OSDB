-- Remove the default_context_id column from environments table.
-- The context ID always defaults to "DEFAULT" and is never configurable per-environment.
-- ParameterResolutionFilter already hardcodes DEFAULT_CONTEXT_ID = "DEFAULT" as the fallback.
ALTER TABLE environments DROP COLUMN IF EXISTS default_context_id;
