-- Automations moved from a single checkpoint (checkpointId) plus an "all except"
-- blocklist (excludedCheckpointIds) to one multi-checkpoint set (checkpointIds).
-- The dialog now only reads/writes checkpointIds, so normalise every existing
-- CHECKPOINT_ENTRY rule's trigger_config JSON here rather than relying on each
-- rule being reopened and saved:
--   * excludedCheckpointIds -> checkpointIds = the game's checkpoints minus the excluded ones
--   * checkpointId (single) -> checkpointIds = [checkpointId]
-- Rules that targeted "any checkpoint" (neither field set) keep an empty set and
-- continue to fire on every checkpoint. trigger_config is TEXT holding JSON, so we
-- cast to jsonb to edit and back to text to store.

-- 1) Materialise an "all except X" blocklist into the explicit set. Only applies to
--    rules without a specific checkpointId (the blocklist was only meaningful there).
UPDATE automation_rules ar
SET trigger_config = (
        (ar.trigger_config::jsonb - 'excludedCheckpointIds')
        || jsonb_build_object(
               'checkpointIds',
               COALESCE(
                   (SELECT jsonb_agg(c.id::text)
                    FROM checkpoints c
                    WHERE c.game_id = ar.game_id
                      AND c.id::text NOT IN (
                          SELECT jsonb_array_elements_text(ar.trigger_config::jsonb -> 'excludedCheckpointIds')
                      )),
                   '[]'::jsonb
               )
           )
    )::text
WHERE ar.trigger_type = 'CHECKPOINT_ENTRY'
  AND (ar.trigger_config::jsonb ->> 'checkpointId') IS NULL
  AND jsonb_typeof(ar.trigger_config::jsonb -> 'excludedCheckpointIds') = 'array'
  AND jsonb_array_length(ar.trigger_config::jsonb -> 'excludedCheckpointIds') > 0;

-- 2) Promote a single checkpointId into a one-element set, dropping the legacy keys.
UPDATE automation_rules
SET trigger_config = (
        (trigger_config::jsonb - 'checkpointId' - 'excludedCheckpointIds')
        || jsonb_build_object('checkpointIds', jsonb_build_array(trigger_config::jsonb -> 'checkpointId'))
    )::text
WHERE trigger_type = 'CHECKPOINT_ENTRY'
  AND (trigger_config::jsonb ->> 'checkpointId') IS NOT NULL;
