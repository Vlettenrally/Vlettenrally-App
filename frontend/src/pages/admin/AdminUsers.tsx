import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormGroup,
  FormControlLabel,
  Checkbox,
  IconButton,
  Typography,
  Alert,
  Paper,
} from '@mui/material';
import { DataGrid, type GridColDef, type GridRenderCellParams } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import LockResetIcon from '@mui/icons-material/LockReset';
import DeleteIcon from '@mui/icons-material/Delete';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import api from '../../services/api';
import type { AdminUser } from '../../types/entities';
import { AdminRole } from '../../types/entities';

export default function AdminUsersPage() {
  const [admins, setAdmins] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Create dialog state
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createUsername, setCreateUsername] = useState('');
  const [createPassword, setCreatePassword] = useState('');
  const [createRoles, setCreateRoles] = useState<Set<AdminRole>>(new Set([AdminRole.ADMIN]));
  const [createError, setCreateError] = useState<string | null>(null);

  // Edit roles dialog state
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editingAdmin, setEditingAdmin] = useState<AdminUser | null>(null);
  const [editRoles, setEditRoles] = useState<Set<AdminRole>>(new Set());
  const [editError, setEditError] = useState<string | null>(null);

  // Reset password dialog state
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [resetAdmin, setResetAdmin] = useState<AdminUser | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const [resetError, setResetError] = useState<string | null>(null);

  // Delete confirmation dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingAdmin, setDeletingAdmin] = useState<AdminUser | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const loadAdmins = useCallback(async () => {
    setLoading(true);
    setError(null);
    const response = await api.listAdmins();
    if (response.data) {
      setAdmins(response.data);
    } else {
      setError(response.error || 'Failed to load admins');
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      const response = await api.listAdmins();
      if (response.data) {
        setAdmins(response.data);
      } else {
        setError(response.error || 'Failed to load admins');
      }
      setLoading(false);
    };
    void fetchData();
  }, []);

  const handleCreateOpen = () => {
    setCreateUsername('');
    setCreatePassword('');
    setCreateRoles(new Set([AdminRole.ADMIN]));
    setCreateError(null);
    setCreateDialogOpen(true);
  };

  const handleCreateSubmit = async () => {
    setCreateError(null);

    // Validate
    if (!createUsername.trim()) {
      setCreateError('Username is required');
      return;
    }
    if (!createPassword) {
      setCreateError('Password is required');
      return;
    }
    if (createRoles.size === 0) {
      setCreateError('At least one role is required');
      return;
    }

    // Check VIEWER combination
    if (createRoles.has(AdminRole.VIEWER) && createRoles.size > 1) {
      setCreateError('VIEWER role cannot be combined with other roles');
      return;
    }

    const response = await api.createAdmin({
      username: createUsername,
      password: createPassword,
      roles: Array.from(createRoles),
    });

    if (response.data) {
      setCreateDialogOpen(false);
      loadAdmins();
    } else {
      setCreateError(response.error || 'Failed to create admin');
    }
  };

  const handleEditOpen = (admin: AdminUser) => {
    setEditingAdmin(admin);
    setEditRoles(new Set(admin.roles));
    setEditError(null);
    setEditDialogOpen(true);
  };

  const handleEditSubmit = async () => {
    if (!editingAdmin) return;
    setEditError(null);

    if (editRoles.size === 0) {
      setEditError('At least one role is required');
      return;
    }

    // Check VIEWER combination
    if (editRoles.has(AdminRole.VIEWER) && editRoles.size > 1) {
      setEditError('VIEWER role cannot be combined with other roles');
      return;
    }

    const response = await api.updateAdminRoles(editingAdmin.id, {
      roles: Array.from(editRoles),
    });

    if (response.data) {
      setEditDialogOpen(false);
      loadAdmins();
    } else {
      setEditError(response.error || 'Failed to update roles');
    }
  };

  const handleResetOpen = (admin: AdminUser) => {
    setResetAdmin(admin);
    setTemporaryPassword(null);
    setResetError(null);
    setResetDialogOpen(true);
  };

  const handleResetSubmit = async () => {
    if (!resetAdmin) return;
    setResetError(null);

    const response = await api.resetAdminPassword(resetAdmin.id);

    if (response.data) {
      setTemporaryPassword(response.data.temporaryPassword);
    } else {
      setResetError(response.error || 'Failed to reset password');
    }
  };

  const handleCopyPassword = () => {
    if (temporaryPassword) {
      navigator.clipboard.writeText(temporaryPassword);
    }
  };

  const handleDeleteOpen = (admin: AdminUser) => {
    setDeletingAdmin(admin);
    setDeleteError(null);
    setDeleteDialogOpen(true);
  };

  const handleDeleteSubmit = async () => {
    if (!deletingAdmin) return;
    setDeleteError(null);

    const response = await api.deactivateAdmin(deletingAdmin.id);

    if (response.status === 204 || response.data !== undefined) {
      setDeleteDialogOpen(false);
      loadAdmins();
    } else {
      setDeleteError(response.error || 'Failed to deactivate admin');
    }
  };

  const toggleCreateRole = (role: AdminRole) => {
    const newRoles = new Set(createRoles);
    if (newRoles.has(role)) {
      newRoles.delete(role);
    } else {
      newRoles.add(role);
    }
    setCreateRoles(newRoles);
  };

  const toggleEditRole = (role: AdminRole) => {
    const newRoles = new Set(editRoles);
    if (newRoles.has(role)) {
      newRoles.delete(role);
    } else {
      newRoles.add(role);
    }
    setEditRoles(newRoles);
  };

  const columns: GridColDef[] = [
    { field: 'username', headerName: 'Username', flex: 1, minWidth: 150 },
    {
      field: 'roles',
      headerName: 'Roles',
      flex: 2,
      minWidth: 300,
      renderCell: (params: GridRenderCellParams<AdminUser>) => (
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', py: 0.5 }}>
          {params.row.roles.map((role: AdminRole) => (
            <Chip
              key={role}
              label={role.replace('_', ' ')}
              size="small"
              color={role === AdminRole.SUPER_ADMIN ? 'secondary' : 'default'}
            />
          ))}
        </Box>
      ),
    },
    {
      field: 'active',
      headerName: 'Status',
      width: 100,
      renderCell: (params: GridRenderCellParams<AdminUser>) => (
        <Chip
          label={params.row.active ? 'Active' : 'Inactive'}
          size="small"
          color={params.row.active ? 'success' : 'default'}
        />
      ),
    },
    {
      field: 'createdAt',
      headerName: 'Created',
      width: 180,
      valueFormatter: (value: string) => new Date(value).toLocaleString(),
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 150,
      sortable: false,
      renderCell: (params: GridRenderCellParams<AdminUser>) => (
        <Box>
          <IconButton
            size="small"
            onClick={() => handleEditOpen(params.row)}
            title="Edit roles"
          >
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            onClick={() => handleResetOpen(params.row)}
            title="Reset password"
          >
            <LockResetIcon fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            onClick={() => handleDeleteOpen(params.row)}
            title="Deactivate"
            disabled={!params.row.active}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Box>
      ),
    },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4">Admin Users</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreateOpen}
        >
          Create Admin
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper>
        <DataGrid
          rows={admins}
          columns={columns}
          loading={loading}
          autoHeight
          pageSizeOptions={[10, 25, 50]}
          initialState={{
            pagination: { paginationModel: { pageSize: 25 } },
          }}
          disableRowSelectionOnClick
          getRowHeight={() => 'auto'}
        />
      </Paper>

      {/* Create Admin Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Admin User</DialogTitle>
        <DialogContent>
          {createError && <Alert severity="error" sx={{ mb: 2 }}>{createError}</Alert>}
          <TextField
            label="Username"
            fullWidth
            value={createUsername}
            onChange={(e) => setCreateUsername(e.target.value)}
            margin="normal"
            autoFocus
          />
          <TextField
            label="Password"
            type="password"
            fullWidth
            value={createPassword}
            onChange={(e) => setCreatePassword(e.target.value)}
            margin="normal"
            helperText="Min 8 chars, must contain uppercase, lowercase, and number"
          />
          <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>
            Roles
          </Typography>
          <FormGroup>
            {Object.values(AdminRole).map((role) => (
              <FormControlLabel
                key={role}
                control={
                  <Checkbox
                    checked={createRoles.has(role)}
                    onChange={() => toggleCreateRole(role)}
                  />
                }
                label={role.replace('_', ' ')}
              />
            ))}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleCreateSubmit} variant="contained">
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Roles Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Roles: {editingAdmin?.username}</DialogTitle>
        <DialogContent>
          {editError && <Alert severity="error" sx={{ mb: 2 }}>{editError}</Alert>}
          <FormGroup>
            {Object.values(AdminRole).map((role) => (
              <FormControlLabel
                key={role}
                control={
                  <Checkbox
                    checked={editRoles.has(role)}
                    onChange={() => toggleEditRole(role)}
                  />
                }
                label={role.replace('_', ' ')}
              />
            ))}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleEditSubmit} variant="contained">
            Save
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reset Password Dialog */}
      <Dialog open={resetDialogOpen} onClose={() => setResetDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Reset Password: {resetAdmin?.username}</DialogTitle>
        <DialogContent>
          {resetError && <Alert severity="error" sx={{ mb: 2 }}>{resetError}</Alert>}
          {temporaryPassword ? (
            <>
              <Alert severity="success" sx={{ mb: 2 }}>
                Password reset successfully
              </Alert>
              <TextField
                label="Temporary Password"
                fullWidth
                value={temporaryPassword}
                InputProps={{
                  readOnly: true,
                  endAdornment: (
                    <IconButton onClick={handleCopyPassword} edge="end" title="Copy to clipboard">
                      <ContentCopyIcon />
                    </IconButton>
                  ),
                }}
                margin="normal"
              />
              <Typography variant="caption" color="text.secondary">
                Share this password securely with the user. They should change it immediately after login.
              </Typography>
            </>
          ) : (
            <Typography>
              This will generate a new temporary password for {resetAdmin?.username}. The user will need to change it after logging in.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResetDialogOpen(false)}>
            {temporaryPassword ? 'Close' : 'Cancel'}
          </Button>
          {!temporaryPassword && (
            <Button onClick={handleResetSubmit} variant="contained" color="warning">
              Reset Password
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Deactivate Admin User</DialogTitle>
        <DialogContent>
          {deleteError && <Alert severity="error" sx={{ mb: 2 }}>{deleteError}</Alert>}
          <Typography>
            Are you sure you want to deactivate <strong>{deletingAdmin?.username}</strong>?
            They will no longer be able to log in.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleDeleteSubmit} variant="contained" color="error">
            Deactivate
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
