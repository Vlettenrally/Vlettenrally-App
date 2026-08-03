-- Emergency pause: a game can be stopped mid-flight and resumed, any number of times.
-- Each pause is one row. Periods (not a "paused since" column on games) because the
-- distance chain must be reproducible: TeamDistanceCalculator derives its counting
-- window purely from timestamps so live accrual and a later rebuild always agree, and
-- a status flag alone cannot tell a rebuild which meters were travelled while frozen.
--
-- Window semantics are half-open [paused_at, resumed_at); resumed_at NULL means the
-- pause is still open, i.e. paused from paused_at onward.
CREATE TABLE game_pause_periods (
    id         UUID PRIMARY KEY,
    game_id    UUID NOT NULL,
    paused_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    resumed_at TIMESTAMP WITH TIME ZONE,           -- NULL = still paused
    reason     TEXT,
    paused_by  UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_gpp_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    CONSTRAINT fk_gpp_paused_by FOREIGN KEY (paused_by) REFERENCES admins(id) ON DELETE SET NULL,
    CONSTRAINT chk_gpp_times CHECK (resumed_at IS NULL OR resumed_at > paused_at)
);

-- Serves the per-game window lookup on the ingest path (ordered by paused_at).
CREATE INDEX idx_gpp_game ON game_pause_periods (game_id, paused_at);

-- At most one open pause per game: a concurrent double-pause violates this instead of
-- silently opening two overlapping windows that nothing would ever close.
CREATE UNIQUE INDEX uq_gpp_open ON game_pause_periods (game_id) WHERE resumed_at IS NULL;

-- Widen the status check to admit the new PAUSED state.
ALTER TABLE games DROP CONSTRAINT chk_game_status;
ALTER TABLE games ADD CONSTRAINT chk_game_status
    CHECK (status IN ('PLANNED', 'ACTIVE', 'PAUSED', 'COMPLETED'));
