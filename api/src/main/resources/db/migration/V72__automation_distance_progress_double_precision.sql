-- The entity maps last_reported_meters / leftover_meters as Java double, so
-- Hibernate schema validation expects DOUBLE PRECISION; V41 created them as
-- NUMERIC. Align the columns with the mapping.
ALTER TABLE automation_distance_progress
    ALTER COLUMN last_reported_meters TYPE DOUBLE PRECISION,
    ALTER COLUMN leftover_meters TYPE DOUBLE PRECISION;
