-- V13: No-op migration (supported_patterns remains VARCHAR, stored as comma-separated values)
-- Originally planned to convert to text[] array, but keeping VARCHAR for H2 compatibility
-- Application layer handles comma-separated parsing via ProviderRegistration entity
SELECT 1;
