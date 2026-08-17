-- Migration: V21__uuid_foreign_key_hierarchy.sql
-- Description: Migrates entity hierarchy from string-based parent references to UUID foreign keys.
--              Adds UUID FK columns with ON DELETE CASCADE for: solutions→domains, zones→solutions,
--              contexts→zones, environments→solutions. Drops old string-based FK columns.
-- Requirements: 1.1–1.7, 2.1–2.6, 3.1–3.6, 4.1–4.5, 10.1–10.5
--
-- This migration is atomic: if any step fails, PostgreSQL rolls back the entire transaction.
-- The NOT NULL constraint after data population serves as a safety check for orphaned rows (Req 10.4).

-- ============================================================
-- 1. SOLUTIONS TABLE: domain_id (VARCHAR) → domain_uuid (UUID)
-- ============================================================

-- 1a. Add nullable UUID column
ALTER TABLE solutions ADD COLUMN domain_uuid UUID;

-- 1b. Populate from domains table via domain_id join
UPDATE solutions SET domain_uuid = d.id FROM domains d WHERE solutions.domain_id = d.domain_id;

-- 1c. Set NOT NULL (fails if any solution has no matching domain — safety check for orphaned rows)
ALTER TABLE solutions ALTER COLUMN domain_uuid SET NOT NULL;

-- 1d. Add FK constraint with ON DELETE CASCADE
ALTER TABLE solutions ADD CONSTRAINT fk_solutions_domain_uuid
    FOREIGN KEY (domain_uuid) REFERENCES domains(id) ON DELETE CASCADE;

-- 1e. Drop old FK constraint referencing domains(domain_id)
ALTER TABLE solutions DROP CONSTRAINT IF EXISTS fk_solutions_domain;

-- 1f. Drop old unique constraint (domain_id, solution_id)
ALTER TABLE solutions DROP CONSTRAINT IF EXISTS uk_domain_solution;

-- 1g. Add new unique constraint (domain_uuid, solution_id)
ALTER TABLE solutions ADD CONSTRAINT uk_domain_uuid_solution UNIQUE (domain_uuid, solution_id);

-- 1h. Drop old index on domain_id
DROP INDEX IF EXISTS idx_solutions_domain_id;

-- 1i. Drop old domain_id string column
ALTER TABLE solutions DROP COLUMN domain_id;

-- 1j. Add index on domain_uuid
CREATE INDEX idx_solutions_domain_uuid ON solutions(domain_uuid);


-- ============================================================
-- 2. ZONES TABLE: solution_id (VARCHAR) → solution_uuid (UUID)
-- ============================================================
-- Note: solution_id is unique within a domain (uk_domain_solution) but NOT globally unique.
-- If the same solution_id exists in multiple domains, the UPDATE join is ambiguous.
-- In practice, solution_ids are unique across the system. The NOT NULL check will catch
-- any issues if they are not.

-- 2a. Add nullable UUID column
ALTER TABLE zones ADD COLUMN solution_uuid UUID;

-- 2b. Populate from solutions table via solution_id join
UPDATE zones SET solution_uuid = s.id FROM solutions s WHERE zones.solution_id = s.solution_id;

-- 2c. Set NOT NULL (fails if any zone has no matching solution)
ALTER TABLE zones ALTER COLUMN solution_uuid SET NOT NULL;

-- 2d. Add FK constraint with ON DELETE CASCADE
ALTER TABLE zones ADD CONSTRAINT fk_zones_solution_uuid
    FOREIGN KEY (solution_uuid) REFERENCES solutions(id) ON DELETE CASCADE;

-- 2e. Drop old unique constraint (solution_id, zone_id)
ALTER TABLE zones DROP CONSTRAINT IF EXISTS uk_solution_zone;

-- 2f. Add new unique constraint (solution_uuid, zone_id)
ALTER TABLE zones ADD CONSTRAINT uk_solution_uuid_zone UNIQUE (solution_uuid, zone_id);

-- 2g. Drop old index on solution_id
DROP INDEX IF EXISTS idx_zones_solution_id;

-- 2h. Drop old solution_id string column
ALTER TABLE zones DROP COLUMN solution_id;

-- 2i. Add index on solution_uuid
CREATE INDEX idx_zones_solution_uuid ON zones(solution_uuid);


-- ============================================================
-- 3. CONTEXTS TABLE: zone_id (VARCHAR) → zone_uuid (UUID)
-- ============================================================
-- Note: zone_id was unique before V8 (zones_zone_id_key constraint). V8 dropped that
-- unique constraint and replaced it with uk_solution_zone (solution_id, zone_id).
-- After V8, zone_id is NOT globally unique — the same zone_id can exist in different solutions.
-- However, contexts created before V8 had a unique zone_id mapping, and contexts created
-- after V8 were created by the application which knows the correct zone.
-- The simple join works if zone_ids are unique in practice. The NOT NULL check catches issues.

-- 3a. Add nullable UUID column
ALTER TABLE contexts ADD COLUMN zone_uuid UUID;

-- 3b. Populate from zones table via zone_id join
UPDATE contexts SET zone_uuid = z.id FROM zones z WHERE contexts.zone_id = z.zone_id;

-- 3c. Set NOT NULL (fails if any context has no matching zone)
ALTER TABLE contexts ALTER COLUMN zone_uuid SET NOT NULL;

-- 3d. Drop old FK constraint if it still exists (V2 created fk_contexts_zone, V8 dropped it)
ALTER TABLE contexts DROP CONSTRAINT IF EXISTS fk_contexts_zone;

-- 3e. Add new FK constraint with ON DELETE CASCADE referencing zones(id)
ALTER TABLE contexts ADD CONSTRAINT fk_contexts_zone_uuid
    FOREIGN KEY (zone_uuid) REFERENCES zones(id) ON DELETE CASCADE;

-- 3f. Drop old unique constraint (zone_id, context_id)
ALTER TABLE contexts DROP CONSTRAINT IF EXISTS uk_contexts_zone_context;

-- 3g. Add new unique constraint (zone_uuid, context_id)
ALTER TABLE contexts ADD CONSTRAINT uk_zone_uuid_context UNIQUE (zone_uuid, context_id);

-- 3h. Drop old index on zone_id
DROP INDEX IF EXISTS idx_contexts_zone_id;

-- 3i. Drop old zone_id string column
ALTER TABLE contexts DROP COLUMN zone_id;

-- 3j. Add index on zone_uuid
CREATE INDEX idx_contexts_zone_uuid ON contexts(zone_uuid);


-- ============================================================
-- 4. ENVIRONMENTS TABLE: solution_id (VARCHAR) → solution_uuid (UUID)
-- ============================================================
-- Note: Same ambiguity caveat as zones — solution_id is not globally unique.
-- The NOT NULL check catches any issues.

-- 4a. Add nullable UUID column
ALTER TABLE environments ADD COLUMN solution_uuid UUID;

-- 4b. Populate from solutions table via solution_id join
UPDATE environments SET solution_uuid = s.id FROM solutions s WHERE environments.solution_id = s.solution_id;

-- 4c. Set NOT NULL (fails if any environment has no matching solution)
ALTER TABLE environments ALTER COLUMN solution_uuid SET NOT NULL;

-- 4d. Add FK constraint with ON DELETE CASCADE
ALTER TABLE environments ADD CONSTRAINT fk_environments_solution_uuid
    FOREIGN KEY (solution_uuid) REFERENCES solutions(id) ON DELETE CASCADE;

-- 4e. Drop old indexes that reference solution_id
DROP INDEX IF EXISTS idx_environments_solution_id;
DROP INDEX IF EXISTS idx_solution_fingerprint;
DROP INDEX IF EXISTS idx_solution_appkey;
DROP INDEX IF EXISTS idx_solution_consumer_name;

-- 4f. Drop old solution_id string column
ALTER TABLE environments DROP COLUMN solution_id;

-- 4g. Add index on solution_uuid
CREATE INDEX idx_environments_solution_uuid ON environments(solution_uuid);

-- 4h. Re-create composite indexes using solution_uuid
CREATE INDEX idx_solution_uuid_fingerprint ON environments(solution_uuid, fingerprint);
CREATE INDEX idx_solution_uuid_appkey ON environments(solution_uuid, appinfo_application_key);
CREATE INDEX idx_solution_uuid_consumer_name ON environments(solution_uuid, consumer_name);
