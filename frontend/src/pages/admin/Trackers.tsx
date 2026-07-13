/**
 * Admin Trackers Page - Manages the global tracker pool (admin / rally_desk only).
 *
 * Features:
 * - Bulk-add pasted tracker IDs to the pool
 * - Per-tracker health: last seen, battery level, last known position
 * - Enable/disable a tracker so it is skipped when assigning trackers to teams
 * - Location button that jumps to the tracker's last position on the admin map
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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Switch,
  IconButton,
} from '@mui/material';
import {
  Add as AddIcon,
  Error as ErrorIcon,
  CheckCircle as SuccessIcon,
  GpsFixed as TrackerIcon,
  MyLocation as LocationIcon,
} from '@mui/icons-material';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { trackerService } from '../../services/trackerService';
import { STALE_AFTER_MS } from './mapConstants';
import type { Tracker } from '../../types/entities';

// Refresh cadence so last-seen/battery stay current while the page is open.
const REFRESH_INTERVAL_MS = 30_000; // 30 seconds

function batteryColor(percentage: number): 'success' | 'warning' | 'error' {
  if (percentage <= 15) return 'error';
  if (percentage <= 35) return 'warning';
  return 'success';
}

export default function Trackers() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();

  const [trackers, setTrackers] = useState<Tracker[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Add-trackers dialog state
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [trackerInput, setTrackerInput] = useState('');
  const [adding, setAdding] = useState(false);
  const [addResult, setAddResult] = useState<{ added: number; duplicates: number } | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

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

  useEffect(() => {
    void loadTrackers();
    const id = setInterval(() => void loadTrackers(), REFRESH_INTERVAL_MS);
    return () => clearInterval(id);
  }, [loadTrackers]);

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

  const handleShowOnMap = (tracker: Tracker) => {
    if (tracker.latitude == null || tracker.longitude == null) return;
    navigate(`/admin/map?flyTo=${tracker.longitude},${tracker.latitude}`);
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
    const stale = Date.now() - lastSeen.getTime() > STALE_AFTER_MS;
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
          {loadError && (
            <Chip label={loadError} color="error" size="small" icon={<ErrorIcon />} />
          )}
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
                      <TableCell>
                        {tracker.assignments.length > 0
                          ? tracker.assignments
                              .map((a) => (a.gameName ? `${a.teamName} — ${a.gameName}` : a.teamName))
                              .join(', ')
                          : '—'}
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip title={tracker.latitude != null ? t('admin.trackers.showOnMap') : ''} arrow>
                          <span>
                            <IconButton
                              size="small"
                              color="primary"
                              disabled={tracker.latitude == null || tracker.longitude == null}
                              onClick={() => handleShowOnMap(tracker)}
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
    </Box>
  );
}
