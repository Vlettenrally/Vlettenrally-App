-- Add RANKING to the allowed values for admin_roles.role
--
-- RANKING has been assignable in the Java Role enum and the admin UI since it was added, but
-- was never added to this constraint — so creating or updating an admin with it failed on
-- insert. Only in production: dev and test run on the in-memory mock repositories, which never
-- see this table.
--
-- TEAM is deliberately still absent: it is a team role and is never stored in admin_roles.
ALTER TABLE admin_roles DROP CONSTRAINT chk_admin_role;
ALTER TABLE admin_roles ADD CONSTRAINT chk_admin_role
    CHECK (role IN ('ADMIN', 'SUPER_ADMIN', 'VIEWER', 'GAME_MASTER', 'MESSAGE_SENDER', 'CHECKPOINT_MANAGER', 'RALLY_DESK', 'RANKING'));
