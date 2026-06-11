-- Automation rules: admin-configured trigger -> actions per game.
-- trigger_config / actions / target_team_ids hold JSON; they are written and
-- read only by the application (mappers), never queried inside, so plain TEXT.

CREATE TABLE automation_rules (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    system BOOLEAN NOT NULL DEFAULT FALSE,          -- built-in rule: cannot be deleted, trigger type fixed
    trigger_type VARCHAR(40) NOT NULL,              -- CHECKPOINT_ENTRY | SCHEDULED_TIME | GAME_STATUS_CHANGE | TEAM_INACTIVITY | RIVAL_OVERTAKE | DISTANCE_TRAVELED
    trigger_config TEXT NOT NULL,                   -- JSON, shape depends on trigger_type
    actions TEXT NOT NULL,                          -- JSON array; empty for DISTANCE_TRAVELED (crediting is intrinsic)
    target_team_ids TEXT NULL,                      -- JSON array of UUIDs; NULL/empty = all teams
    fire_once BOOLEAN NOT NULL DEFAULT TRUE,        -- at most once per (rule, team, context)
    cooldown_minutes INTEGER NULL,                  -- re-fire interval when fire_once = false
    created_by UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_automation_rules_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);

CREATE INDEX idx_automation_rules_game ON automation_rules (game_id);
CREATE INDEX idx_automation_rules_game_trigger ON automation_rules (game_id, trigger_type) WHERE enabled;

-- Execution log: audit trail for admins AND dedup state for fire_once/cooldown.
CREATE TABLE automation_executions (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL,
    game_id UUID NOT NULL,
    team_id UUID NULL,                              -- NULL for game-level firings
    context_key VARCHAR(100) NULL,                  -- dedup discriminator: checkpoint id, "PLANNED->ACTIVE", rival id
    status VARCHAR(20) NOT NULL,                    -- SUCCESS | PARTIAL | FAILED
    detail TEXT NULL,                               -- JSON: per-action results / error messages
    fired_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_automation_executions_rule FOREIGN KEY (rule_id) REFERENCES automation_rules(id) ON DELETE CASCADE
);

CREATE INDEX idx_automation_executions_rule_fired ON automation_executions (rule_id, fired_at DESC);
CREATE INDEX idx_automation_executions_dedup ON automation_executions (rule_id, team_id, context_key);

-- Continuous accrual state for DISTANCE_TRAVELED rules. leftover_meters carries
-- the remainder below one whole 100 m block so a mid-game rate change only
-- affects future meters.
CREATE TABLE automation_distance_progress (
    rule_id UUID NOT NULL,
    team_id UUID NOT NULL,
    last_reported_meters NUMERIC NOT NULL,          -- provider's cumulative value at last poll
    leftover_meters NUMERIC NOT NULL DEFAULT 0,     -- credited-eligible meters not yet converted to a whole point
    credited_points INTEGER NOT NULL DEFAULT 0,
    point_entry_id UUID NULL,                       -- the single growing ledger entry for this team
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (rule_id, team_id),
    CONSTRAINT fk_automation_distance_rule FOREIGN KEY (rule_id) REFERENCES automation_rules(id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_distance_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- Every game carries a built-in "Afstandspunten" rule. Existing games get it
-- disabled so no event suddenly starts crediting distance points mid-rally;
-- new games receive it enabled at creation time (application code).
INSERT INTO automation_rules (id, game_id, name, enabled, system, trigger_type, trigger_config, actions, fire_once)
SELECT gen_random_uuid(), id, 'Afstandspunten', FALSE, TRUE, 'DISTANCE_TRAVELED', '{"pointsPer100m":1}', '[]', FALSE
FROM games;
