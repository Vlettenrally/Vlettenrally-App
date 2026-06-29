-- Admin-uploaded pictures are not attributed to a team; allow team_id to be NULL.
ALTER TABLE pictures ALTER COLUMN team_id DROP NOT NULL;
