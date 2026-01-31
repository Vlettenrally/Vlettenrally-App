-- Device registrations table
-- Either team_id or admin_id must be set, but not both
CREATE TABLE device_registrations (
    id UUID PRIMARY KEY,
    team_id UUID,
    admin_id UUID,
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    -- XOR constraint: either team_id or admin_id, but not both or neither
    CONSTRAINT chk_device_owner CHECK (
        (team_id IS NOT NULL AND admin_id IS NULL) OR 
        (team_id IS NULL AND admin_id IS NOT NULL)
    ),
    
    CONSTRAINT fk_device_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_device_registrations_team_id ON device_registrations(team_id);
CREATE INDEX idx_device_registrations_admin_id ON device_registrations(admin_id);
CREATE INDEX idx_device_registrations_last_seen ON device_registrations(last_seen);
