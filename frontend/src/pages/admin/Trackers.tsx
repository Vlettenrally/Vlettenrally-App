/**
 * Admin Trackers Page - Manages the global tracker pool (admin / rally_desk only).
 *
 * Features:
 * - Bulk-add pasted tracker IDs to the pool
 * - Bulk-delete trackers, preselecting the ones not assigned to a game
 * - Per-tracker health: last seen, battery level, last known position
 * - Enable/disable a tracker so it is skipped when assigning trackers to teams
 * - Location button that shows the tracker's last position on a map, in a popup
 */
import {
  Box,
  Typography,
  Button,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  CircularProgress,
  Tooltip,
  Alert,
  Checkbox,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  TextField,
  Switch,
  IconButton,
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Error as ErrorIcon,
  CheckCircle as SuccessIcon,
  GpsFixed as TrackerIcon,
  MyLocation as LocationIcon,
} from '@mui/icons-material';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { trackerService } from '../../services/trackerService';
import MapPopupDialog from '../../components/admin/MapPopupDialog';
import { STALE_AFTER_MS } from './mapConstants';
import { batteryColor } from '../../utils/trackerStatus';
import type { Tracker } from '../../types/entities';

// Refresh cadence so last-seen/battery stay current while the page is open.
const REFRESH_INTERVAL_MS = 30_000; // 30 seconds

// The teams/games holding this tracker, or null when it is free. Only teams in a
// planned or active game count, so a completed game's trackers read as unassigned.
function assignmentLabel(tracker: Tracker): string | null {
  if (tracker.assignments.length === 0) return null;
  return tracker.assignments
    .map((a) => (a.gameName ? `${a.teamName} — ${a.gameName}` : a.teamName))
    .join(', ');
}

export default function Trackers() {
  const { t, i18n } = useTranslation();

  const [trackers, setTrackers] = useState<Tracker[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Tracker whose position is shown in the map popup, or null when it is closed.
  // Holds the row as it was clicked, so a background refresh cannot move the pin
  // out from under the user.
  const [mapTarget, setMapTarget] = useState<Tracker | null>(null);

  // Add-trackers dialog state
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [trackerInput, setTrackerInput] = useState('');
  const [adding, setAdding] = useState(false);
  const [addResult, setAddResult] = useState<{ added: number; duplicates: number } | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

  // Delete-trackers dialog state. `selectedIds` survives a background refresh
  // because it holds tracker IDs, not rows; IDs that vanish are filtered on submit.
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [deleting, setDeleting] = useState(false);
  const [deleteResult, setDeleteResult] = useState<{ deleted: number } | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  const loadTrackers = useCallback(async () => {
    try {
      setTrackers(await trackerService.getTrackers());
      setLoadError(null);
    } catch {
      setLoadError(t('admin.trackers.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  // Hold the refresh while the delete dialogs are open, so rows don't shift
  // under the user mid-selection.
  const picking = deleteDialogOpen || confirmOpen;

  useEffect(() => {
    void loadTrackers();
  }, [loadTrackers]);

  useEffect(() => {
    if (picking) return;
    const id = setInterval(() => void loadTrackers(), REFRESH_INTERVAL_MS);
    return () => clearInterval(id);
  }, [loadTrackers, picking]);

  // Ticks the stale/fresh styling of last-seen timestamps between data refreshes.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), REFRESH_INTERVAL_MS);
    return () => clearInterval(id);
  }, []);

  const handleAddTrackers = async () => {
    // Split the pasted blob into individual tracker IDs (one per line, also
    // tolerating commas/whitespace between IDs); the backend de-dupes and trims.
    const trackerIds = trackerInput
      .split(/[\s,;]+/)
      .map((id) => id.trim())
      .filter((id) => id.length > 0);
    if (trackerIds.length === 0) return;

    setAdding(true);
    setAddResult(null);
    setAddError(null);

    try {
      const result = await trackerService.addTrackers(trackerIds);
      setAddResult(result);
      setAddDialogOpen(false);
      setTrackerInput('');
      await loadTrackers();
    } catch (err) {
      setAddError(err instanceof Error ? err.message : t('admin.trackers.addFailed'));
    } finally {
      setAdding(false);
    }
  };

  const handleToggleEnabled = async (tracker: Tracker) => {
    const enabled = !tracker.enabled;
    // Optimistic update; reconciled by the reload (or rolled back on failure).
    setTrackers((prev) =>
      prev.map((item) => (item.trackerId === tracker.trackerId ? { ...item, enabled } : item)),
    );
    try {
      await trackerService.setEnabled(tracker.trackerId, enabled);
    } catch {
      setTrackers((prev) =>
        prev.map((item) =>
          item.trackerId === tracker.trackerId ? { ...item, enabled: tracker.enabled } : item,
        ),
      );
      setAddError(t('admin.trackers.updateFailed'));
    }
  };

  // Preselect every tracker that no team currently holds — the common case is
  // clearing out the ones nobody is using — but leave the user free to adjust.
  const handleOpenDeleteDialog = () => {
    setSelectedIds(
      new Set(trackers.filter((tk) => tk.assignments.length === 0).map((tk) => tk.trackerId)),
    );
    setDeleteError(null);
    setDeleteResult(null);
    setDeleteDialogOpen(true);
  };

  const toggleSelected = (trackerId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (!next.delete(trackerId)) {
        next.add(trackerId);
      }
      return next;
    });
  };

  const toggleSelectAll = () => {
    setSelectedIds((prev) =>
      prev.size === trackers.length ? new Set() : new Set(trackers.map((tk) => tk.trackerId)),
    );
  };

  // Only trackers still in the list: one may have been deleted elsewhere between
  // opening the dialog and confirming.
  const selectedTrackers = trackers.filter((tk) => selectedIds.has(tk.trackerId));
  const selectedAssigned = selectedTrackers.filter((tk) => tk.assignments.length > 0);

  const handleDeleteConfirm = async () => {
    setDeleting(true);
    setDeleteError(null);
    try {
      const result = await trackerService.deleteTrackers(
        selectedTrackers.map((tk) => tk.trackerId),
      );
      setDeleteResult(result);
      setConfirmOpen(false);
      setDeleteDialogOpen(false);
      setSelectedIds(new Set());
      await loadTrackers();
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : t('admin.trackers.deleteFailed'));
    } finally {
      setDeleting(false);
    }
  };

  const renderLastSeen = (tracker: Tracker) => {
    if (!tracker.lastSeenAt) {
      return (
        <Typography variant="body2" color="text.disabled">
          {t('admin.trackers.neverSeen')}
        </Typography>
      );
    }
    const lastSeen = new Date(tracker.lastSeenAt);
    const stale = now - lastSeen.getTime() > STALE_AFTER_MS;
    return (
      <Typography variant="body2" color={stale ? 'text.disabled' : 'text.primary'}>
        {lastSeen.toLocaleString(i18n.language)}
      </Typography>
    );
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          {t('admin.trackers.title')}
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          {addResult && (
            <Chip
              label={t('admin.trackers.addResult', addResult)}
              color="success"
              size="small"
              icon={<SuccessIcon />}
              onDelete={() => setAddResult(null)}
            />
          )}
          {addError && (
            <Chip
              label={addError}
              color="error"
              size="small"
              icon={<ErrorIcon />}
              onDelete={() => setAddError(null)}
            />
          )}
          {deleteResult && (
            <Chip
              label={t('admin.trackers.deleteResult', deleteResult)}
              color="success"
              size="small"
              icon={<SuccessIcon />}
              onDelete={() => setDeleteResult(null)}
            />
          )}
          {loadError && (
            <Chip label={loadError} color="error" size="small" icon={<ErrorIcon />} />
          )}
          <Button
            variant="outlined"
            color="error"
            startIcon={<DeleteIcon />}
            disabled={trackers.length === 0}
            onClick={handleOpenDeleteDialog}
          >
            {t('admin.trackers.deleteTrackers')}
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => {
              setAddError(null);
              setAddDialogOpen(true);
            }}
          >
            {t('admin.trackers.addTrackers')}
          </Button>
        </Box>
      </Box>
      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {t('admin.trackers.trackersLoaded', { count: trackers.length })}
          </Typography>
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
              <CircularProgress />
            </Box>
          ) : trackers.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              {t('admin.trackers.noTrackers')}
            </Typography>
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell><strong>{t('admin.trackers.columnTracker')}</strong></TableCell>
                    <TableCell align="center"><strong>{t('admin.trackers.columnEnabled')}</strong></TableCell>
                    <TableCell align="right"><strong>{t('admin.trackers.columnBattery')}</strong></TableCell>
                    <TableCell><strong>{t('admin.trackers.columnLastSeen')}</strong></TableCell>
                    <TableCell><strong>{t('admin.trackers.columnAssignedTo')}</strong></TableCell>
                    <TableCell align="center"><strong>{t('admin.trackers.columnLocation')}</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {trackers.map((tracker) => (
                    <TableRow key={tracker.trackerId} hover sx={{ opacity: tracker.enabled ? 1 : 0.55 }}>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <TrackerIcon fontSize="small" color={tracker.enabled ? 'action' : 'disabled'} />
                          {tracker.trackerId}
                        </Box>
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip title={tracker.enabled ? '' : t('admin.trackers.disabledTooltip')} arrow>
                          <Switch
                            checked={tracker.enabled}
                            onChange={() => handleToggleEnabled(tracker)}
                            size="small"
                          />
                        </Tooltip>
                      </TableCell>
                      <TableCell align="right">
                        {tracker.batteryPercentage != null ? (
                          <Chip
                            label={`${tracker.batteryPercentage}%`}
                            color={batteryColor(tracker.batteryPercentage)}
                            size="small"
                            variant="outlined"
                          />
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell>{renderLastSeen(tracker)}</TableCell>
                      <TableCell>{assignmentLabel(tracker) ?? '—'}</TableCell>
                      <TableCell align="center">
                        <Tooltip title={tracker.latitude != null ? t('admin.trackers.showOnMap') : ''} arrow>
                          <span>
                            <IconButton
                              size="small"
                              color="primary"
                              disabled={tracker.latitude == null || tracker.longitude == null}
                              onClick={() => setMapTarget(tracker)}
                            >
                              <LocationIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      <Dialog open={addDialogOpen} onClose={() => setAddDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t('admin.trackers.addDialogTitle')}</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('admin.trackers.addDialogHint')}
          </Typography>
          <TextField
            label={t('admin.trackers.trackerIdsLabel')}
            value={trackerInput}
            onChange={(e) => setTrackerInput(e.target.value)}
            multiline
            rows={10}
            fullWidth
            autoFocus
            placeholder={'860123456789012\n860123456789013\n860123456789014'}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            variant="contained"
            onClick={handleAddTrackers}
            disabled={adding || trackerInput.trim().length === 0}
            startIcon={adding ? <CircularProgress size={20} /> : <AddIcon />}
          >
            {adding ? t('admin.trackers.adding') : t('admin.trackers.add')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={deleteDialogOpen}
        onClose={() => setDeleteDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{t('admin.trackers.deleteDialogTitle')}</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" color="text.secondary">
            {t('admin.trackers.deleteDialogHint')}
          </Typography>
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
          <List dense>
            <ListItemButton onClick={toggleSelectAll}>
              <ListItemIcon sx={{ minWidth: 42 }}>
                <Checkbox
                  edge="start"
                  disableRipple
                  tabIndex={-1}
                  checked={trackers.length > 0 && selectedIds.size === trackers.length}
                  indeterminate={selectedIds.size > 0 && selectedIds.size < trackers.length}
                />
              </ListItemIcon>
              <ListItemText
                primary={t('admin.trackers.selectAll')}
                slotProps={{ primary: { fontWeight: 'bold' } }}
              />
            </ListItemButton>
            {trackers.map((tracker) => {
              const assignedTo = assignmentLabel(tracker);
              return (
                <ListItemButton
                  key={tracker.trackerId}
                  onClick={() => toggleSelected(tracker.trackerId)}
                >
                  <ListItemIcon sx={{ minWidth: 42 }}>
                    <Checkbox
                      edge="start"
                      disableRipple
                      tabIndex={-1}
                      checked={selectedIds.has(tracker.trackerId)}
                    />
                  </ListItemIcon>
                  <ListItemText
                    primary={tracker.trackerId}
                    secondary={assignedTo ?? t('admin.trackers.notAssigned')}
                  />
                  {assignedTo && (
                    <Chip label={t('admin.trackers.columnAssignedTo')} color="warning" size="small" />
                  )}
                </ListItemButton>
              );
            })}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            variant="contained"
            color="error"
            startIcon={<DeleteIcon />}
            disabled={selectedTrackers.length === 0}
            onClick={() => setConfirmOpen(true)}
          >
            {`${t('admin.trackers.delete')} (${selectedTrackers.length})`}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>
          {t('admin.trackers.confirmDeleteTitle', { count: selectedTrackers.length })}
        </DialogTitle>
        <DialogContent dividers>
          <DialogContentText>{t('admin.trackers.confirmDeleteBody')}</DialogContentText>
          {selectedAssigned.length > 0 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {t('admin.trackers.assignedWarning')}
              <Box component="ul" sx={{ pl: 3, mb: 0, mt: 1 }}>
                {selectedAssigned.map((tracker) => (
                  <li key={tracker.trackerId}>
                    {`${tracker.trackerId} → ${assignmentLabel(tracker)}`}
                  </li>
                ))}
              </Box>
            </Alert>
          )}
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)} disabled={deleting}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDeleteConfirm}
            disabled={deleting}
            startIcon={deleting ? <CircularProgress size={20} /> : <DeleteIcon />}
          >
            {deleting ? t('admin.trackers.deleting') : t('admin.trackers.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Mounted only while a tracker is picked: MapView takes `center` at mount
          time, so the dialog has to unmount in between to recentre on the next one. */}
      {mapTarget?.latitude != null && mapTarget.longitude != null && (
        <MapPopupDialog
          open
          onClose={() => setMapTarget(null)}
          lat={mapTarget.latitude}
          lng={mapTarget.longitude}
          label={mapTarget.trackerId}
          title={t('admin.trackers.mapDialogTitle', { trackerId: mapTarget.trackerId })}
        />
      )}
    </Box>
  );
}
