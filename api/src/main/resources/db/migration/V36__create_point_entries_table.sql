-- Points ledger: every change to a team's total_points is recorded here.
-- An entry's current_amount may be edited (admin "penalty" or correction);
-- original_amount is immutable so the UI can show original + deducted delta.

CREATE TABLE point_entries (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    game_id UUID NOT NULL,
    entry_type VARCHAR(40) NOT NULL,                -- CHECKPOINT_VERIFICATION | MANUAL_ADJUSTMENT
    original_amount INTEGER NOT NULL,               -- immutable, signed
    current_amount INTEGER NOT NULL,                -- mutable via edits, signed
    source_achievement_id UUID NULL,
    location GEOMETRY(Point, 4326) NULL,            -- WGS84, optional
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NULL,                           -- populated only for MANUAL_ADJUSTMENT
    edited_at TIMESTAMP WITH TIME ZONE NULL,
    edited_by UUID NULL,
    edit_reason TEXT NULL,

    CONSTRAINT fk_point_entries_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_point_entries_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);

CREATE INDEX idx_point_entries_game_created ON point_entries (game_id, created_at DESC);
CREATE INDEX idx_point_entries_team_created ON point_entries (team_id, created_at DESC);
CREATE INDEX idx_point_entries_location_gix ON point_entries USING GIST (location);

-- Optimistic locking for safe concurrent total_points mutations
ALTER TABLE teams ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
