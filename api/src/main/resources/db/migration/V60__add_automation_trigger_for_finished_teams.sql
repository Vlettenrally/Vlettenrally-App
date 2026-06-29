ALTER TABLE automation_rules
    ADD COLUMN trigger_for_finished_teams boolean NOT NULL DEFAULT false;
