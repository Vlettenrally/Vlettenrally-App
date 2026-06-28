-- Fix automation_rules rows where action objects are missing the "type" discriminator.
-- This was caused by AutomationRuleMapper serializing List<AutomationAction> via
-- writeValueAsString(Object), which erased the generic type in GraalVM native image
-- and silently dropped the @JsonTypeInfo "type" field.
--
-- Type is inferred from whichever fields are present on the action object:
--   messageContent / messageType / messageIcon / messageTemplateId → SEND_MESSAGE
--   points / pointsDescription                                     → AWARD_POINTS
--   adminNotification                                              → NOTIFY_ADMINS
--   rankingsPublished                                              → SET_RANKINGS_PUBLISHED
--   publicRankingsEnabled                                          → SET_PUBLIC_RANKINGS_ENABLED
--   {} (no recognisable fields)                                    → FINISH_TEAM

UPDATE automation_rules
SET actions = (
    SELECT jsonb_agg(
        CASE
            WHEN elem ? 'type' THEN elem
            WHEN elem ? 'messageContent'
              OR elem ? 'messageType'
              OR elem ? 'messageIcon'
              OR elem ? 'messageTemplateId'
                THEN elem || '{"type":"SEND_MESSAGE"}'::jsonb
            WHEN elem ? 'points'
              OR elem ? 'pointsDescription'
                THEN elem || '{"type":"AWARD_POINTS"}'::jsonb
            WHEN elem ? 'adminNotification'
                THEN elem || '{"type":"NOTIFY_ADMINS"}'::jsonb
            WHEN elem ? 'rankingsPublished'
                THEN elem || '{"type":"SET_RANKINGS_PUBLISHED"}'::jsonb
            WHEN elem ? 'publicRankingsEnabled'
                THEN elem || '{"type":"SET_PUBLIC_RANKINGS_ENABLED"}'::jsonb
            ELSE elem || '{"type":"FINISH_TEAM"}'::jsonb
        END
    )::text
    FROM jsonb_array_elements(actions::jsonb) AS elem
)
WHERE actions IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements(actions::jsonb) AS elem
      WHERE NOT (elem ? 'type')
  );
