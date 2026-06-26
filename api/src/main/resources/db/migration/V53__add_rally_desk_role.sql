-- Add RALLY_DESK to the allowed values for admin_roles.role
ALTER TABLE admin_roles DROP CONSTRAINT chk_admin_role;
ALTER TABLE admin_roles ADD CONSTRAINT chk_admin_role
    CHECK (role IN ('ADMIN', 'SUPER_ADMIN', 'VIEWER', 'GAME_MASTER', 'MESSAGE_SENDER', 'CHECKPOINT_MANAGER', 'RALLY_DESK'));
