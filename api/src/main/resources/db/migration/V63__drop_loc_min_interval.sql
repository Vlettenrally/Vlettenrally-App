-- The per-fix min-interval throttle guard (DROP_THROTTLE) has been removed. High-frequency
-- fixes are now accepted; re-polled duplicates / non-monotonic fixes are still dropped by a
-- fixed "timestamp must strictly advance" rule in TrackOutlierFilter. The per-game override
-- column is therefore obsolete.
ALTER TABLE games DROP COLUMN loc_min_interval_seconds;
