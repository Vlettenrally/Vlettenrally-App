CREATE TABLE pictures (
    id            UUID PRIMARY KEY,
    team_id       UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    game_id       UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    object_key    TEXT NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_at   TIMESTAMPTZ,
    moderated_at  TIMESTAMPTZ,
    moderated_by  UUID REFERENCES admins(id),
    notes         TEXT,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pictures_team_id ON pictures(team_id);
CREATE INDEX idx_pictures_game_id ON pictures(game_id);
CREATE INDEX idx_pictures_moderation ON pictures(status, uploaded_at);
