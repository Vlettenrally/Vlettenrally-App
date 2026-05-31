-- Multi-device check-in coordination: nickname + gameId on device registrations,
-- and a main_device_id on teams used to gate the participants/emergency-phones step.

ALTER TABLE device_registrations ADD COLUMN nickname VARCHAR(120);
ALTER TABLE device_registrations ADD COLUMN game_id UUID;

CREATE INDEX idx_device_registrations_game_team
    ON device_registrations(game_id, team_id);

ALTER TABLE teams ADD COLUMN main_device_id UUID;
