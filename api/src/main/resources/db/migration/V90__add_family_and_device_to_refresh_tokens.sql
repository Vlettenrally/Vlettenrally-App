-- Scope refresh-token revocation to one rotation lineage instead of a whole subject.
--
-- All of a team's devices share one subject (the team id), so the replay tripwire's
-- deleteBySubject logged out every phone in the team — and a participant's only way back in is a
-- physical check-in at the rally desk. family_id identifies one login on one device across all its
-- rotations; device_id lets the Valkey access-token cutoff be device-scoped too.
--
-- Both columns are nullable on purpose: during a rolling deploy, pods running the previous version
-- keep inserting rows without them, and the replay path falls back to walking replaced_by_token.

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN device_id UUID;

-- How many times this superseded token has been resolved back to its lineage's live head. A client
-- that lost a rotation response replays once and then stores what it got back; something that keeps
-- presenting the same superseded token is not storing anything, which is the reuse signal worth
-- acting on. Counting is what makes the tripwire independent of elapsed time.
ALTER TABLE refresh_tokens ADD COLUMN replay_count INTEGER NOT NULL DEFAULT 0;

-- Backfill by walking the existing replaced_by_token chains, so a lineage that already spans
-- several rotations keeps one family id rather than fragmenting into one family per row.
--
-- The CYCLE clause is not decoration: a chain that loops back on itself (which nothing should
-- produce, but which no constraint prevents) makes an unguarded recursive CTE run forever, and a
-- migration that never finishes takes the whole deployment down with it. Rows reached only via a
-- cycle are dropped here and picked up by the fallback below.
WITH RECURSIVE chain(root, id, replaced_by_token) AS (
    SELECT r.id, r.id, r.replaced_by_token
      FROM refresh_tokens r
     WHERE NOT EXISTS (SELECT 1 FROM refresh_tokens p WHERE p.replaced_by_token = r.token)
    UNION ALL
    SELECT c.root, n.id, n.replaced_by_token
      FROM chain c
      JOIN refresh_tokens n ON n.token = c.replaced_by_token
) CYCLE id SET is_cycle USING path
UPDATE refresh_tokens t SET family_id = c.root FROM chain c WHERE t.id = c.id AND NOT c.is_cycle;

-- Anything the walk could not reach (a cycle, or a row whose ancestor is already gone) becomes
-- the root of its own family.
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

-- Deliberately NOT unique: "one live token per family" is the intent, but a constraint violation
-- on the refresh path would surface as a 500 and a logged-out team — exactly the failure class
-- this migration exists to remove. Revisit once the auth.refresh.replay metric shows no forks.
CREATE INDEX idx_refresh_tokens_family_active ON refresh_tokens(family_id) WHERE revoked = false;
CREATE INDEX idx_refresh_tokens_subject_device ON refresh_tokens(subject, device_id);
