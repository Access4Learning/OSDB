-- V44: Make provider_registry endpoint column nullable
-- DELAYED-only providers do not need an endpoint URL (they poll their queue).
-- Only IMMEDIATE providers require an endpoint for callback delivery.

ALTER TABLE provider_registry ALTER COLUMN endpoint DROP NOT NULL;
