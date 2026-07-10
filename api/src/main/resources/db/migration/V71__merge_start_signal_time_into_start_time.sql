-- Merge start_signal_time into start_time. The signal time was the value all
-- behavior keyed off (activation, polling lead window, checkpoint eligibility,
-- automation anchors), so it wins where both were set.
UPDATE games SET start_time = start_signal_time WHERE start_signal_time IS NOT NULL;
ALTER TABLE games DROP COLUMN start_signal_time;
