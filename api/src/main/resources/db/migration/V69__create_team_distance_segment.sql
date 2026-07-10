-- One row per counted movement segment between two fixes. The chain itself encodes
-- all distance-accrual state: the last row's endpoint IS the anchor (last counted
-- fix), and a team's travelled distance is simply SUM(meters). Replaces the mutable
-- team_distance_progress accumulator (anchor + cursor + running total).
--
-- Plain table on purpose — NOT a hypertable, no retention/compression: segments must
-- outlive location_data's 30-day retention, since they carry the distance history.
CREATE TABLE team_distance_segment (
    id          UUID PRIMARY KEY,
    team_id     UUID NOT NULL,
    from_time   TIMESTAMP WITH TIME ZONE,          -- NULL only on the per-team seed/carryover row
    from_lon    DOUBLE PRECISION,
    from_lat    DOUBLE PRECISION,
    to_time     TIMESTAMP WITH TIME ZONE NOT NULL, -- GPS time of the counted fix (= the anchor)
    to_lon      DOUBLE PRECISION NOT NULL,
    to_lat      DOUBLE PRECISION NOT NULL,
    meters      DOUBLE PRECISION NOT NULL CHECK (meters >= 0),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tds_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT chk_tds_times CHECK (from_time IS NULL OR from_time < to_time),
    -- Chain integrity: a given anchor can be extended at most once, and there is at
    -- most one seed (NULL from_time) per team. A concurrent double-write violates
    -- this constraint instead of silently double-counting distance.
    CONSTRAINT uq_tds_from UNIQUE NULLS NOT DISTINCT (team_id, from_time)
);

-- Serves the last-segment (anchor) lookup, the recompute delete range, and an
-- index-only SUM(meters) per team via the INCLUDE column.
CREATE UNIQUE INDEX uq_tds_team_to_time
    ON team_distance_segment (team_id, to_time) INCLUDE (meters);

-- Cutover: one carryover seed per team preserves the exact cumulative total and
-- anchor from the old accumulator, immune to location_data retention (the fixes
-- that produced the old total may already be gone). to_time is the old cursor
-- (>= the true anchor time), so the next live fix is always strictly newer.
INSERT INTO team_distance_segment (id, team_id, from_time, to_time, to_lon, to_lat, meters)
SELECT gen_random_uuid(), team_id, NULL, last_processed_time, anchor_lon, anchor_lat, cumulative_meters
FROM team_distance_progress
WHERE anchor_lon IS NOT NULL AND anchor_lat IS NOT NULL AND last_processed_time IS NOT NULL;
