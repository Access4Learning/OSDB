-- Add SIF 3.7 environment fields and flattened application_info columns
-- Source: SIF 3.7 XSD environmentType + applicationInfoType
-- Design decision: application_info flattened into environments table (1:1, always read together)
-- Naming convention follows legacy schema pattern (appinfo_ prefix)

-- ============================================================
-- 1. SIF 3.7 environment-level fields
-- ============================================================

ALTER TABLE environments ADD COLUMN fingerprint VARCHAR(255);
ALTER TABLE environments ADD COLUMN authentication_method VARCHAR(50);
ALTER TABLE environments ADD COLUMN instance_id VARCHAR(255);
ALTER TABLE environments ADD COLUMN user_token VARCHAR(512);
ALTER TABLE environments ADD COLUMN consumer_name VARCHAR(255);

-- ============================================================
-- 2. Flattened application_info fields (XSD applicationInfoType)
-- ============================================================

ALTER TABLE environments ADD COLUMN appinfo_application_key VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_supported_infra_version VARCHAR(50);
ALTER TABLE environments ADD COLUMN appinfo_data_model_namespace VARCHAR(512);
ALTER TABLE environments ADD COLUMN appinfo_transport VARCHAR(50);
ALTER TABLE environments ADD COLUMN appinfo_app_vendor_name VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_app_product_name VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_app_product_version VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_app_icon_uri VARCHAR(512);
ALTER TABLE environments ADD COLUMN appinfo_adapter_vendor_name VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_adapter_product_name VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_adapter_product_version VARCHAR(255);
ALTER TABLE environments ADD COLUMN appinfo_adapter_icon_uri VARCHAR(512);

-- ============================================================
-- 3. Indexes for lookup performance
-- ============================================================

-- Index on fingerprint within a solution for fast lookups
CREATE INDEX idx_solution_fingerprint ON environments(solution_id, fingerprint);

-- Index on application key within a solution
CREATE INDEX idx_solution_appkey ON environments(solution_id, appinfo_application_key);

-- Index on consumer name within a solution
CREATE INDEX idx_solution_consumer_name ON environments(solution_id, consumer_name);
