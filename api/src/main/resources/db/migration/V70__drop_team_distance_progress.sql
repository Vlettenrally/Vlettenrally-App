-- The accumulator's state (running total + anchor) was fully carried over into
-- team_distance_segment by V69's per-team seed rows. Dropped immediately rather
-- than kept for rollback: the old code reading a by-then-stale cursor/cumulative
-- would mis-credit distance anyway, so a lingering table adds risk, not safety.
DROP TABLE team_distance_progress;
