-- Persistent inbox items for admin users (incoming admin-targeted notifications)
CREATE TABLE admin_notifications (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL,
    team_id UUID,
    source VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    read_by_admin_id UUID,

    CONSTRAINT fk_admin_notification_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_notification_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_notification_read_by_admin FOREIGN KEY (read_by_admin_id) REFERENCES admins(id) ON DELETE SET NULL
);

CREATE INDEX idx_admin_notifications_game_id ON admin_notifications(game_id);
CREATE INDEX idx_admin_notifications_game_created_at ON admin_notifications(game_id, created_at DESC);
CREATE INDEX idx_admin_notifications_game_read ON admin_notifications(game_id, read);
