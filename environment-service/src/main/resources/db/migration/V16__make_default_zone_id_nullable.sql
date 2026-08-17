-- Migration: V16__make_default_zone_id_nullable.sql
-- Description: Makes default_zone_id nullable per SIF 3.7 spec (defaultZone is optional).

ALTER TABLE environments ALTER COLUMN default_zone_id DROP NOT NULL;
