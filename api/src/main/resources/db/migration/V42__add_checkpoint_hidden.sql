-- Zones can be hidden from teams' map while still driving automations.
ALTER TABLE checkpoints ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
