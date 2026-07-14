-- Record how a team was checked in (rally desk manual flow vs the team's own phones).
-- Without this, "checked in manually" and "checked in digitally but the team never
-- finished" are both checked_in = TRUE / checked_in_digitally = FALSE and cannot be
-- told apart. NULL = not checked in.

ALTER TABLE teams
    ADD COLUMN check_in_method VARCHAR(10)
        CHECK (check_in_method IN ('MANUAL', 'DIGITAL'));

-- Backfill so existing rows keep the meaning they are displayed with today.
UPDATE teams
SET check_in_method = CASE WHEN checked_in_digitally THEN 'DIGITAL' ELSE 'MANUAL' END
WHERE checked_in = TRUE;
