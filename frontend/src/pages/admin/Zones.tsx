import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
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
  Paper,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  CircularProgress,
  Alert,
  Chip,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  CloudDownload as ImportIcon,
  ContentCopy as CopyIcon,
  VisibilityOff as HiddenIcon,
} from '@mui/icons-material';
import api from '../../services/api';
import { useGameStore } from '../../store/useGameStore';
import type { Checkpoint } from '../../types/entities';
import ZoneFormDialog from '../../components/ZoneFormDialog';
import CopyZonesDialog from '../../components/CopyZonesDialog';

const formatDateTime = (dateStr?: string): string => {
  if (!dateStr) return '–';
  try {
    return new Date(dateStr).toLocaleString(undefined, {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  } catch {
    return '–';
  }
};

const formatLocation = (lat?: number, lng?: number): string => {
  if (lat == null || lng == null) return '–';
  return `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
};

const Zones = () => {
  const { t } = useTranslation();
  const { selectedGameId, games } = useGameStore();
  const selectedGame = games.find((g) => g.id === selectedGameId) ?? null;

  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [loading, setLoading] = useState(() => !!selectedGameId);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Form dialog state
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [selectedCheckpoint, setSelectedCheckpoint] = useState<Checkpoint | null>(null);

  // Delete dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [checkpointToDelete, setCheckpointToDelete] = useState<Checkpoint | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Import state
  const [importing, setImporting] = useState(false);

  // Copy-zones dialog state
  const [copyDialogOpen, setCopyDialogOpen] = useState(false);

  const fetchCheckpoints = useCallback(async () => {
    if (!selectedGameId) return;
    setLoading(true);
    setError(null);
    try {
      const response = await api.getCheckpoints(selectedGameId);
      if (response.data) {
        setCheckpoints(response.data);
      } else {
        setError(response.error || t('admin.zones.loadFailed'));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('admin.zones.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [selectedGameId, t]);

  // Drop the previous game's checkpoints the moment the selection changes
  // (adjust-state-during-render, not an effect).
  const [prevGameId, setPrevGameId] = useState(selectedGameId);
  if (prevGameId !== selectedGameId) {
    setPrevGameId(selectedGameId);
    setCheckpoints([]);
    setError(null);
    setLoading(!!selectedGameId);
  }

  useEffect(() => {
    if (!selectedGameId) return;
    let cancelled = false;
    api
      .getCheckpoints(selectedGameId)
      .then((response) => {
        if (cancelled) return;
        if (response.data) setCheckpoints(response.data);
        else setError(response.error || t('admin.zones.loadFailed'));
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : t('admin.zones.loadFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedGameId, t]);

  const handleCreate = () => {
    setSelectedCheckpoint(null);
    setFormDialogOpen(true);
  };

  const handleEdit = (checkpoint: Checkpoint) => {
    setSelectedCheckpoint(checkpoint);
    setFormDialogOpen(true);
  };

  const handleDeleteClick = (checkpoint: Checkpoint) => {
    setCheckpointToDelete(checkpoint);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!checkpointToDelete || !selectedGameId) return;
    setDeleting(true);
    try {
      const response = await api.deleteCheckpoint(selectedGameId, checkpointToDelete.id);
      if (response.error) {
        setError(response.error);
      } else {
        setDeleteDialogOpen(false);
        setCheckpointToDelete(null);
        await fetchCheckpoints();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('admin.zones.deleteFailed'));
    } finally {
      setDeleting(false);
    }
  };

  const handleFormClose = () => {
    setFormDialogOpen(false);
    setSelectedCheckpoint(null);
  };

  const handleFormSave = async () => {
    await fetchCheckpoints();
  };

  const handleImport = async () => {
    if (!selectedGameId) return;
    setImporting(true);
    setError(null);
    setSuccess(null);
    try {
      const response = await api.importCheckpoints(selectedGameId);
      if (response.error) {
        setError(response.error);
      } else if (response.data) {
        const { created, updated, errors } = response.data;
        const parts: string[] = [];
        if (created > 0) parts.push(t('admin.zones.importCreated', { count: created }));
        if (updated > 0) parts.push(t('admin.zones.importUpdated', { count: updated }));
        if (errors > 0) parts.push(t('admin.zones.importErrors', { count: errors }));
        setSuccess(
          parts.length > 0
            ? t('admin.zones.importDone', { details: parts.join(', ') })
            : t('admin.zones.importDoneNoChanges')
        );
        await fetchCheckpoints();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('admin.zones.importFailed'));
    } finally {
      setImporting(false);
    }
  };

  if (!selectedGameId) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 4 }}>
          {t('admin.zones.title')}
        </Typography>
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              {t('admin.zones.selectGameFirst')}
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          {t('admin.zones.title')}
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {selectedGame?.locationEndpointUrl && (
            <Button
              variant="outlined"
              startIcon={importing ? <CircularProgress size={18} /> : <ImportIcon />}
              onClick={handleImport}
              disabled={importing}
            >
              {importing ? t('admin.zones.importing') : t('admin.zones.importFromTracker')}
            </Button>
          )}
          <Button
            variant="outlined"
            startIcon={<CopyIcon />}
            onClick={() => setCopyDialogOpen(true)}
          >
            {t('admin.zones.copyZones')}
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreate}>
            {t('admin.zones.addZone')}
          </Button>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      {loading && checkpoints.length === 0 ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      ) : checkpoints.length === 0 ? (
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              {t('admin.zones.noZones')}
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>{t('admin.zones.columnName')}</strong></TableCell>
                <TableCell><strong>{t('admin.zones.columnPoints')}</strong></TableCell>
                <TableCell><strong>{t('admin.zones.columnLocation')}</strong></TableCell>
                <TableCell><strong>{t('admin.zones.columnOpenTime')}</strong></TableCell>
                <TableCell><strong>{t('admin.zones.columnCloseTime')}</strong></TableCell>
                <TableCell align="right"><strong>{t('admin.zones.columnActions')}</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {checkpoints.map((cp) => (
                <TableRow key={cp.id} hover>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Typography variant="body1" fontWeight="medium">
                        {cp.name}
                      </Typography>
                      {cp.hidden && (
                        <Chip
                          size="small"
                          icon={<HiddenIcon sx={{ fontSize: 14 }} />}
                          label={t('admin.zones.hidden')}
                          variant="outlined"
                          color="default"
                          title={t('admin.zones.hiddenTooltip')}
                        />
                      )}
                    </Box>
                    {cp.externalId && (
                      <Typography variant="caption" color="text.secondary">
                        {t('admin.zones.external', { id: cp.externalId })}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{cp.points}</TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {formatLocation(cp.latitude, cp.longitude)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {cp.zoneType === 'polygon'
                        ? t('admin.zones.polygon', { count: cp.polygonPoints?.length ?? 0 })
                        : t('admin.zones.circle', { radius: cp.radiusMeters ?? 50 })}
                    </Typography>
                  </TableCell>
                  <TableCell>{formatDateTime(cp.openingTime)}</TableCell>
                  <TableCell>{formatDateTime(cp.closingTime)}</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => handleEdit(cp)} title={t('admin.zones.editZone')}>
                      <EditIcon />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleDeleteClick(cp)}
                      color="error"
                      title={t('admin.zones.deleteZone')}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Copy Zones Dialog */}
      <CopyZonesDialog
        open={copyDialogOpen}
        onClose={() => setCopyDialogOpen(false)}
        gameId={selectedGameId}
        onCopied={fetchCheckpoints}
      />

      {/* Zone Form Dialog */}
      <ZoneFormDialog
        open={formDialogOpen}
        onClose={handleFormClose}
        checkpoint={selectedCheckpoint}
        gameId={selectedGameId}
        onSave={handleFormSave}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>{t('admin.zones.deleteZone')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('admin.zones.deleteConfirm', { name: checkpointToDelete?.name })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
          >
            {deleting ? <CircularProgress size={20} /> : t('common.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Zones;
