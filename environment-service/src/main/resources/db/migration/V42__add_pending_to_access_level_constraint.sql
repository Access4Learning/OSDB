-- Add PENDING to the access_level check constraint
-- Required for non-admin self-provisioning flow where access level is forced to PENDING

ALTER TABLE acl_entries DROP CONSTRAINT IF EXISTS chk_access_level;
ALTER TABLE acl_entries ADD CONSTRAINT chk_access_level 
    CHECK (access_level IN ('APPROVED', 'SUPPORTED', 'REJECTED', 'UNSUPPORTED', 'REQUESTED', 'PENDING'));
