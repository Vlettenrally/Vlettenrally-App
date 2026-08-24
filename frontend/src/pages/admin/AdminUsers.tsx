import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@mui/material';
import { DataGrid, type GridColDef, type GridRenderCellParams } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import BlockIcon from '@mui/icons-material/Block';
import EditIcon from '@mui/icons-material/Edit';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import LockResetIcon from '@mui/icons-material/LockReset';
import DeleteForeverIcon from '@mui/icons-material/DeleteForever';
import RestoreIcon from '@mui/icons-material/Restore';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import api from '../../services/api';
import { ADMIN_PAGE_ROLES } from '../../services/auth';
import type { AdminReferences, AdminUser } from '../../types/entities';
import { AdminRole } from '../../types/entities';

/** Reference counts in display order, paired with the i18n key that labels each one. */
const REFERENCE_LABEL_KEYS: Array<[keyof AdminReferences, string]> = [
  ['moderatedPictures', 'admin.adminUsers.referenceModeratedPictures'],
  ['sentMessages', 'admin.adminUsers.referenceSentMessages'],
  ['verifiedAchievements', 'admin.adminUsers.referenceVerifiedAchievements'],
  ['pointEntries', 'admin.adminUsers.referencePointEntries'],
  ['automationRules', 'admin.adminUsers.referenceAutomationRules'],
];

const PAGE_KEYS: Record<string, string> = {
  '/admin/map':                'admin.layout.map',
  '/admin/inbox':              'admin.layout.inbox',
  '/admin/games':              'admin.layout.games',
  '/admin/teams':              'admin.layout.teams',
  '/admin/check-in':           'admin.layout.checkIn',
  '/admin/checkout':           'admin.layout.checkout',
  '/admin/zones':              'admin.layout.zones',
  '/admin/automations':        'admin.layout.automations',
  '/admin/settings':           'admin.layout.settings',
  '/admin/pictures':           'admin.layout.pictures',
  '/admin/messages':           'admin.layout.messages',
  '/admin/points-management':  'admin.layout.pointsManagement',
  '/admin/checkpoint-station': 'admin.layout.checkpointStation',
  '/admin/admin-users':        'admin.layout.adminUsers',
};

const ALWAYS_ACCESSIBLE = ['/admin/map', '/admin/settings', '/admin/pictures'];

export default function AdminUsersPage() {
  const { t } = useTranslation();

  const pageLabel = useCallback((path: string) => t(PAGE_KEYS[path] || path, { defaultValue: path }), [t]);

  const getRolePages = useCallback((role: AdminRole): string[] => {
    if (role === AdminRole.ADMIN) {
      return Object.entries(PAGE_KEYS)
        .filter(([path]) => path !== '/admin/admin-users')
        .map(([path]) => pageLabel(path));
    }
    const paths = [
      ...ALWAYS_ACCESSIBLE,
      ...Object.entries(ADMIN_PAGE_ROLES)
        .filter(([, roles]) => roles.includes(role))
        .map(([path]) => path),
    ];
    return [...new Set(paths)].map((p) => pageLabel(p)).filter(Boolean);
  }, [pageLabel]);

  const [admins, setAdmins] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createUsername, setCreateUsername] = useState('');
  const [createPassword, setCreatePassword] = useState('');
  const [createRoles, setCreateRoles] = useState<Set<AdminRole>>(new Set([AdminRole.ADMIN]));
  const [createError, setCreateError] = useState<string | null>(null);

  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editingAdmin, setEditingAdmin] = useState<AdminUser | null>(null);
  const [editRoles, setEditRoles] = useState<Set<AdminRole>>(new Set());
  const [editError, setEditError] = useState<string | null>(null);

  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [resetAdmin, setResetAdmin] = useState<AdminUser | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const [resetError, setResetError] = useState<string | null>(null);

  const [deactivateDialogOpen, setDeactivateDialogOpen] = useState(false);
  const [deactivatingAdmin, setDeactivatingAdmin] = useState<AdminUser | null>(null);
  const [deactivateError, setDeactivateError] = useState<string | null>(null);

  const [activateDialogOpen, setActivateDialogOpen] = useState(false);
  const [activatingAdmin, setActivatingAdmin] = useState<AdminUser | null>(null);
  const [activateError, setActivateError] = useState<string | null>(null);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingAdmin, setDeletingAdmin] = useState<AdminUser | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteReferences, setDeleteReferences] = useState<AdminReferences | null>(null);
  const [deleteReferencesLoading, setDeleteReferencesLoading] = useState(false);

  const loadAdmins = useCallback(async () => {
    setLoading(true);
    setError(null);
    const response = await api.listAdmins();
    if (response.data) {
      setAdmins(response.data);
    } else {
      setError(response.error || t('admin.adminUsers.failedToLoad'));
    }
    setLoading(false);
  }, [t]);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      const response = await api.listAdmins();
      if (response.data) {
        setAdmins(response.data);
      } else {
        setError(response.error || t('admin.adminUsers.failedToLoad'));
      }
      setLoading(false);
    };
    void fetchData();
  }, [t]);

  const handleCreateOpen = () => {
    setCreateUsername('');
    setCreatePassword('');
    setCreateRoles(new Set([AdminRole.ADMIN]));
    setCreateError(null);
    setCreateDialogOpen(true);
  };

  const handleCreateSubmit = async () => {
    setCreateError(null);

    if (!createUsername.trim()) {
      setCreateError(t('admin.adminUsers.usernameRequired'));
      return;
    }
    if (!createPassword) {
      setCreateError(t('admin.adminUsers.passwordRequired'));
      return;
    }
    if (createRoles.size === 0) {
      setCreateError(t('admin.adminUsers.roleRequired'));
      return;
    }

    if (createRoles.has(AdminRole.VIEWER) && createRoles.size > 1) {
      setCreateError(t('admin.adminUsers.viewerCombinationError'));
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
      setCreateError(response.error || t('admin.adminUsers.failedToCreate'));
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
      setEditError(t('admin.adminUsers.roleRequired'));
      return;
    }

    if (editRoles.has(AdminRole.VIEWER) && editRoles.size > 1) {
      setEditError(t('admin.adminUsers.viewerCombinationError'));
      return;
    }

    const response = await api.updateAdminRoles(editingAdmin.id, {
      roles: Array.from(editRoles),
    });

    if (response.data) {
      setEditDialogOpen(false);
      loadAdmins();
    } else {
      setEditError(response.error || t('admin.adminUsers.failedToUpdate'));
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
      setResetError(response.error || t('admin.adminUsers.failedToReset'));
    }
  };

  const handleCopyPassword = () => {
    if (temporaryPassword) {
      navigator.clipboard.writeText(temporaryPassword);
    }
  };

  const handleDeactivateOpen = (admin: AdminUser) => {
    setDeactivatingAdmin(admin);
    setDeactivateError(null);
    setDeactivateDialogOpen(true);
  };

  const handleDeactivateSubmit = async () => {
    if (!deactivatingAdmin) return;
    setDeactivateError(null);

    const response = await api.deactivateAdmin(deactivatingAdmin.id);

    if (response.data) {
      setDeactivateDialogOpen(false);
      loadAdmins();
    } else {
      setDeactivateError(response.error || t('admin.adminUsers.failedToDeactivate'));
    }
  };

  const handleActivateOpen = (admin: AdminUser) => {
    setActivatingAdmin(admin);
    setActivateError(null);
    setActivateDialogOpen(true);
  };

  const handleActivateSubmit = async () => {
    if (!activatingAdmin) return;
    setActivateError(null);

    const response = await api.activateAdmin(activatingAdmin.id);

    if (response.data) {
      setActivateDialogOpen(false);
      loadAdmins();
    } else {
      setActivateError(response.error || t('admin.adminUsers.failedToActivate'));
    }
  };

  const handleDeleteOpen = async (admin: AdminUser) => {
    setDeletingAdmin(admin);
    setDeleteError(null);
    setDeleteReferences(null);
    setDeleteReferencesLoading(true);
    setDeleteDialogOpen(true);

    // Look up what the admin is credited on before offering the button, so a blocked delete
    // is explained up front rather than as a 409 after the user commits to it.
    const response = await api.getAdminReferences(admin.id);
    if (response.data) {
      setDeleteReferences(response.data);
    } else {
      setDeleteError(response.error || t('admin.adminUsers.failedToDelete'));
    }
    setDeleteReferencesLoading(false);
  };

  const handleDeleteSubmit = async () => {
    if (!deletingAdmin) return;
    setDeleteError(null);

    const response = await api.deleteAdmin(deletingAdmin.id);

    if (response.status === 204) {
      setDeleteDialogOpen(false);
      loadAdmins();
    } else {
      setDeleteError(response.error || t('admin.adminUsers.failedToDelete'));
    }
  };

  // Non-zero counts, ready to render. Empty means the admin is safe to delete.
  const blockingReferences = deleteReferences
    ? REFERENCE_LABEL_KEYS
        .filter(([field]) => deleteReferences[field] > 0)
        .map(([field, key]) => t(key, { count: deleteReferences[field] }))
    : [];

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
    { field: 'username', headerName: t('admin.adminUsers.columnUsername'), flex: 1, minWidth: 150 },
    {
      field: 'roles',
      headerName: t('admin.adminUsers.columnRoles'),
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
      headerName: t('admin.adminUsers.columnStatus'),
      width: 100,
      renderCell: (params: GridRenderCellParams<AdminUser>) => (
        <Chip
          label={params.row.active ? t('admin.adminUsers.statusActive') : t('admin.adminUsers.statusInactive')}
          size="small"
          color={params.row.active ? 'success' : 'default'}
        />
      ),
    },
    {
      field: 'createdAt',
      headerName: t('admin.adminUsers.columnCreated'),
      width: 180,
      valueFormatter: (value: string) => new Date(value).toLocaleString(undefined),
    },
    {
      field: 'actions',
      headerName: t('admin.adminUsers.columnActions'),
      width: 180,
      sortable: false,
      renderCell: (params: GridRenderCellParams<AdminUser>) => (
        <Box>
          <IconButton
            size="small"
            onClick={() => handleEditOpen(params.row)}
            title={t('admin.adminUsers.tooltipEditRoles')}
          >
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            onClick={() => handleResetOpen(params.row)}
            title={t('admin.adminUsers.tooltipResetPassword')}
          >
            <LockResetIcon fontSize="small" />
          </IconButton>
          {params.row.active ? (
            <IconButton
              size="small"
              onClick={() => handleDeactivateOpen(params.row)}
              title={t('admin.adminUsers.tooltipDeactivate')}
            >
              <BlockIcon fontSize="small" />
            </IconButton>
          ) : (
            <>
              <IconButton
                size="small"
                onClick={() => handleActivateOpen(params.row)}
                title={t('admin.adminUsers.tooltipActivate')}
              >
                <RestoreIcon fontSize="small" />
              </IconButton>
              <IconButton
                size="small"
                color="error"
                onClick={() => handleDeleteOpen(params.row)}
                title={t('admin.adminUsers.tooltipDelete')}
              >
                <DeleteForeverIcon fontSize="small" />
              </IconButton>
            </>
          )}
        </Box>
      ),
    },
  ];

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>{t('admin.adminUsers.title')}</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreateOpen}
        >
          {t('admin.adminUsers.createAdmin')}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper sx={{ display: 'flex', flexDirection: 'column', height: 500 }}>
        <DataGrid
          rows={admins}
          columns={columns}
          loading={loading}
          pageSizeOptions={[10, 25, 50]}
          initialState={{
            pagination: { paginationModel: { pageSize: 25 } },
          }}
          disableRowSelectionOnClick
          getRowHeight={() => 'auto'}
        />
      </Paper>

      <Accordion sx={{ mt: 2 }}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle1">{t('admin.adminUsers.roleReference')}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('admin.adminUsers.roleTableRole')}</TableCell>
                <TableCell>{t('admin.adminUsers.roleTablePages')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {Object.values(AdminRole).map((role) => (
                <TableRow key={role}>
                  <TableCell sx={{ whiteSpace: 'nowrap', fontWeight: 500 }}>
                    {role.replace(/_/g, ' ')}
                  </TableCell>
                  <TableCell>{getRolePages(role).join(', ')}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </AccordionDetails>
      </Accordion>

      {/* Create Admin Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.adminUsers.createDialogTitle')}</DialogTitle>
        <DialogContent>
          {createError && <Alert severity="error" sx={{ mb: 2 }}>{createError}</Alert>}
          <TextField
            label={t('admin.adminUsers.usernameLabel')}
            fullWidth
            value={createUsername}
            onChange={(e) => setCreateUsername(e.target.value)}
            margin="normal"
            autoFocus
          />
          <TextField
            label={t('admin.adminUsers.passwordLabel')}
            type="password"
            fullWidth
            value={createPassword}
            onChange={(e) => setCreatePassword(e.target.value)}
            margin="normal"
            helperText={t('admin.adminUsers.passwordHint')}
          />
          <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>
            {t('admin.adminUsers.rolesLabel')}
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
                label={
                  <Box>
                    <Typography variant="body2">{role.replace(/_/g, ' ')}</Typography>
                    <Typography variant="caption" color="textSecondary">
                      {getRolePages(role).join(', ')}
                    </Typography>
                  </Box>
                }
                sx={{ alignItems: 'flex-start', mb: 0.5 }}
              />
            ))}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button onClick={handleCreateSubmit} variant="contained">
            {t('admin.adminUsers.createAdmin')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Roles Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.adminUsers.editDialogTitle', { username: editingAdmin?.username })}</DialogTitle>
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
                label={
                  <Box>
                    <Typography variant="body2">{role.replace(/_/g, ' ')}</Typography>
                    <Typography variant="caption" color="textSecondary">
                      {getRolePages(role).join(', ')}
                    </Typography>
                  </Box>
                }
                sx={{ alignItems: 'flex-start', mb: 0.5 }}
              />
            ))}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button onClick={handleEditSubmit} variant="contained">
            {t('common.save')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reset Password Dialog */}
      <Dialog open={resetDialogOpen} onClose={() => setResetDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.adminUsers.resetDialogTitle', { username: resetAdmin?.username })}</DialogTitle>
        <DialogContent>
          {resetError && <Alert severity="error" sx={{ mb: 2 }}>{resetError}</Alert>}
          {temporaryPassword ? (
            <>
              <Alert severity="success" sx={{ mb: 2 }}>
                {t('admin.adminUsers.resetSuccess')}
              </Alert>
              <TextField
                label={t('admin.adminUsers.temporaryPasswordLabel')}
                fullWidth
                value={temporaryPassword}
                slotProps={{
                  input: {
                    readOnly: true,
                    endAdornment: (
                      <IconButton onClick={handleCopyPassword} edge="end" title={t('common.copy')}>
                        <ContentCopyIcon />
                      </IconButton>
                    ),
                  },
                }}
                margin="normal"
              />
              <Typography variant="caption" color="textSecondary">
                {t('admin.adminUsers.shareSecurely')}
              </Typography>
            </>
          ) : (
            <Typography>
              {t('admin.adminUsers.resetConfirmText', { username: resetAdmin?.username })}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResetDialogOpen(false)}>
            {temporaryPassword ? t('common.close') : t('common.cancel')}
          </Button>
          {!temporaryPassword && (
            <Button onClick={handleResetSubmit} variant="contained" color="warning">
              {t('admin.adminUsers.resetPasswordAction')}
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Deactivate Confirmation Dialog */}
      <Dialog open={deactivateDialogOpen} onClose={() => setDeactivateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.adminUsers.deactivateDialogTitle')}</DialogTitle>
        <DialogContent>
          {deactivateError && <Alert severity="error" sx={{ mb: 2 }}>{deactivateError}</Alert>}
          <Typography>
            {t('admin.adminUsers.deactivateConfirmText', { username: deactivatingAdmin?.username })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeactivateDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button onClick={handleDeactivateSubmit} variant="contained" color="error">
            {t('admin.adminUsers.deactivateAction')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reactivate Confirmation Dialog */}
      <Dialog open={activateDialogOpen} onClose={() => setActivateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.adminUsers.activateDialogTitle')}</DialogTitle>
        <DialogContent>
          {activateError && <Alert severity="error" sx={{ mb: 2 }}>{activateError}</Alert>}
          <Typography>
            {t('admin.adminUsers.activateConfirmText', { username: activatingAdmin?.username })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActivateDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button onClick={handleActivateSubmit} variant="contained">
            {t('admin.adminUsers.activateAction')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Permanent Delete Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.adminUsers.deleteDialogTitle')}</DialogTitle>
        <DialogContent>
          {deleteError && <Alert severity="error" sx={{ mb: 2 }}>{deleteError}</Alert>}
          {deleteReferencesLoading ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1 }}>
              <CircularProgress size={20} />
              <Typography>{t('admin.adminUsers.deleteCheckingReferences')}</Typography>
            </Box>
          ) : blockingReferences.length > 0 ? (
            <Alert severity="warning">
              <Typography>
                {t('admin.adminUsers.deleteBlockedIntro', { username: deletingAdmin?.username })}
              </Typography>
              <List dense sx={{ listStyleType: 'disc', pl: 3, py: 0.5 }}>
                {blockingReferences.map((label) => (
                  <ListItem key={label} sx={{ display: 'list-item', p: 0 }}>
                    <ListItemText primary={label} />
                  </ListItem>
                ))}
              </List>
              <Typography variant="body2">{t('admin.adminUsers.deleteBlockedHint')}</Typography>
            </Alert>
          ) : deleteReferences ? (
            <>
              <Typography>
                {t('admin.adminUsers.deleteConfirmText', { username: deletingAdmin?.username })}
              </Typography>
              <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                {t('admin.adminUsers.deleteWarningIrreversible')}
              </Typography>
            </>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>
            {blockingReferences.length > 0 ? t('common.close') : t('common.cancel')}
          </Button>
          {deleteReferences && blockingReferences.length === 0 && (
            <Button onClick={handleDeleteSubmit} variant="contained" color="error">
              {t('admin.adminUsers.deleteAction')}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
}
