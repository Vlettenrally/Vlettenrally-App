-- Relax the location_data retention policy (was 1 day in V28).
--
-- getLatestTeamLocations omits any team with zero rows, so a team whose history aged out
-- vanished from the admin map. Legendstracking-polled teams were kept alive by the 30s
-- poller; Traccar teams only produce rows while their device is actively posting, so once
-- quiet their rows were dropped after 1 day and the team disappeared. Widen retention to
-- 30 days so the last-known position survives any event. Compression (chunks > 1h) still
-- bounds storage.
--
-- add_retention_policy with if_not_exists does NOT update an existing policy, so the 1-day
-- policy must be removed first, then re-added with the new interval.
SELECT remove_retention_policy('location_data', if_exists => TRUE);
SELECT add_retention_policy('location_data', INTERVAL '30 days', if_not_exists => TRUE);
