-- Align enum values with SIF 3.7 specification
-- This migration updates check constraints to match the official SIF 3.7 XSD definitions:
--   environmentTypeType: DIRECT, BROKERED (+ ADMIN as broker extension)
--   rightTypeType: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE
--   rightValueType: APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED

-- ============================================================
-- 1. Environment type: CONSUMER/PROVIDER/BROKER/ADMIN -> DIRECT/BROKERED/ADMIN
-- ============================================================

-- Migrate existing data to new values
UPDATE environments SET type = 'BROKERED' WHERE type IN ('CONSUMER', 'PROVIDER', 'BROKER');

-- Drop old constraint and add new one
ALTER TABLE environments DROP CONSTRAINT IF EXISTS chk_type;
ALTER TABLE environments ADD CONSTRAINT chk_type CHECK (type IN ('DIRECT', 'BROKERED', 'ADMIN'));

-- ============================================================
-- 2. Access type: PROVIDE/CONSUME/SUBSCRIBE -> QUERY/CREATE/UPDATE/DELETE/SUBSCRIBE/PROVIDE
-- ============================================================

-- Migrate existing data: CONSUME -> QUERY (closest equivalent for read access)
UPDATE acl_entries SET access_type = 'QUERY' WHERE access_type = 'CONSUME';

-- Drop old constraint and add new one
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS chk_access_type;
ALTER TABLE acl_entries ADD CONSTRAINT chk_access_type 
    CHECK (access_type IN ('QUERY', 'CREATE', 'UPDATE', 'DELETE', 'SUBSCRIBE', 'PROVIDE'));

-- ============================================================
-- 3. Access level: APPROVED/REJECTED/PENDING -> APPROVED/SUPPORTED/REJECTED/UNSUPPORTED/REQUESTED
-- ============================================================

-- Migrate existing data: PENDING -> REQUESTED
UPDATE acl_entries SET access_level = 'REQUESTED' WHERE access_level = 'PENDING';

-- Drop old constraint and add new one
ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS chk_access_level;
ALTER TABLE acl_entries ADD CONSTRAINT chk_access_level 
    CHECK (access_level IN ('APPROVED', 'SUPPORTED', 'REJECTED', 'UNSUPPORTED', 'REQUESTED'));
