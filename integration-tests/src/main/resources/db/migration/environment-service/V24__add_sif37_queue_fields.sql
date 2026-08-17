-- Add SIF 3.7 queue fields to the queues table
-- Adds: polling, queue_uri, max_concurrent_connections, last_accessed, last_modified
-- Renames: created_at -> created

ALTER TABLE queues ADD COLUMN IF NOT EXISTS polling VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE';
ALTER TABLE queues ADD COLUMN IF NOT EXISTS queue_uri VARCHAR(512);
ALTER TABLE queues ADD COLUMN IF NOT EXISTS max_concurrent_connections INTEGER NOT NULL DEFAULT 1;
ALTER TABLE queues ADD COLUMN IF NOT EXISTS last_accessed TIMESTAMP;
ALTER TABLE queues ADD COLUMN IF NOT EXISTS last_modified TIMESTAMP;
ALTER TABLE queues RENAME COLUMN created_at TO created;
