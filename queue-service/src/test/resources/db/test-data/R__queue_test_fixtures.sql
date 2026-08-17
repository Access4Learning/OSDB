-- Repeatable migration: Test fixtures for queue-service integration tests.
-- Creates a minimal domain → solution → environment hierarchy so that
-- queues can reference a valid owner_id (FK to environments.id).
--
-- This file is idempotent — uses ON CONFLICT DO NOTHING.

-- Test domain
INSERT INTO domains (id, domain_id, description, created_at)
VALUES ('d47ac10b-58cc-4372-a567-0e02b2c3d479', 'QUEUE_TEST_DOMAIN', 'Queue test domain', NOW())
ON CONFLICT (id) DO NOTHING;

-- Test solution
INSERT INTO solutions (id, domain_uuid, solution_id, description, created_at)
VALUES ('7c9e6679-7425-40de-944b-e07fc1f90ae7',
        'd47ac10b-58cc-4372-a567-0e02b2c3d479',
        'QUEUE_TEST_SOLUTION', 'Queue test solution', NOW())
ON CONFLICT (id) DO NOTHING;

-- Test environment (used as queue owner)
INSERT INTO environments (id, application_name, session_token, shared_secret, type,
                          solution_uuid, created_at, authentication_method)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        'queue-test-env', 'queue-test-token', 'queue-test-secret', 'BROKERED',
        '7c9e6679-7425-40de-944b-e07fc1f90ae7', NOW(), 'SIF_HMACSHA256')
ON CONFLICT (id) DO NOTHING;

-- Second test environment (used for owner-filtering tests that need two distinct owners)
INSERT INTO environments (id, application_name, session_token, shared_secret, type,
                          solution_uuid, created_at, authentication_method)
VALUES ('b2c3d4e5-f6a7-8901-bcde-f12345678901',
        'queue-test-env-2', 'queue-test-token-2', 'queue-test-secret-2', 'BROKERED',
        '7c9e6679-7425-40de-944b-e07fc1f90ae7', NOW(), 'SIF_HMACSHA256')
ON CONFLICT (id) DO NOTHING;
