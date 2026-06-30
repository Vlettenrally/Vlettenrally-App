-- Minutes before the start signal to begin polling/ingesting locations (0 = off).
-- Lets teams sitting inside a start zone at kickoff be detected immediately.
ALTER TABLE games ADD COLUMN location_polling_lead_minutes INTEGER NOT NULL DEFAULT 30;
