-- Create admin_roles junction table
CREATE TABLE admin_roles (
    admin_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    
    CONSTRAINT fk_admin_role_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE CASCADE,
    CONSTRAINT chk_admin_role CHECK (role IN ('ADMIN', 'SUPER_ADMIN', 'VIEWER', 'GAME_MASTER', 'MESSAGE_SENDER', 'CHECKPOINT_MANAGER'))
);

-- Indexes for efficient queries
CREATE INDEX idx_admin_roles_admin_id ON admin_roles(admin_id);
CREATE INDEX idx_admin_roles_role ON admin_roles(role);

-- Migrate existing admins to have ADMIN and SUPER_ADMIN roles
INSERT INTO admin_roles (admin_id, role)
SELECT id, 'ADMIN' FROM admins WHERE active = TRUE;

INSERT INTO admin_roles (admin_id, role)
SELECT id, 'SUPER_ADMIN' FROM admins WHERE active = TRUE;

-- Drop email column and its index
DROP INDEX IF EXISTS idx_admins_email;
ALTER TABLE admins DROP COLUMN email;
