-- Final standings stay hidden from players until an admin explicitly reveals
-- them (announced live at the award ceremony). This flag persists that reveal so
-- it survives players reloading or reconnecting. Set via POST /games/{id}/rankings/publish.
ALTER TABLE games ADD COLUMN rankings_published boolean NOT NULL DEFAULT false;
