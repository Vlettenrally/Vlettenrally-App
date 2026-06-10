-- Store the Legendstracking event_participants_id alongside the existing external_id
-- (which holds the WordPress ticket_booking_id). Populated during sync-event-details
-- by matching the bib reported by the tracker to the team's name.

ALTER TABLE teams
    ADD COLUMN external_participant_id INTEGER;

CREATE INDEX idx_teams_external_participant_id ON teams(external_participant_id);

CREATE UNIQUE INDEX idx_teams_game_external_participant_id
    ON teams(game_id, external_participant_id)
    WHERE external_participant_id IS NOT NULL;
