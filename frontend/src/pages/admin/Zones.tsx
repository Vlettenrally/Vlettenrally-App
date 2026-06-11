import { useEffect, useState, useCallback } from 'react';
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
    return new Date(dateStr).toLocaleString('nl-NL', {
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
  const { selectedGameId, games } = useGameStore();
  const selectedGame = games.find((g) => g.id === selectedGameId) ?? null;

  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [loading, setLoading] = useState(false);
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
        setError(response.error || 'Zones laden mislukt');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Zones laden mislukt');
    } finally {
      setLoading(false);
    }
  }, [selectedGameId]);

  useEffect(() => {
    if (selectedGameId) {
      fetchCheckpoints();
    } else {
      setCheckpoints([]);
    }
  }, [selectedGameId, fetchCheckpoints]);

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
      setError(err instanceof Error ? err.message : 'Verwijderen mislukt');
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
        if (created > 0) parts.push(`${created} zone(s) aangemaakt`);
        if (updated > 0) parts.push(`${updated} zone(s) bijgewerkt`);
        if (errors > 0) parts.push(`${errors} fouten`);
        setSuccess(
          parts.length > 0
            ? `Import voltooid: ${parts.join(', ')}`
            : 'Import voltooid: geen wijzigingen'
        );
        await fetchCheckpoints();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Import mislukt');
    } finally {
      setImporting(false);
    }
  };

  if (!selectedGameId) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 4 }}>
          Zones
        </Typography>
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              Selecteer een game in de zijbalk om zones te beheren.
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
          Zones
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {selectedGame?.locationEndpointUrl && (
            <Button
              variant="outlined"
              startIcon={importing ? <CircularProgress size={18} /> : <ImportIcon />}
              onClick={handleImport}
              disabled={importing}
            >
              Zones importeren uit tracker
            </Button>
          )}
          <Button
            variant="outlined"
            startIcon={<CopyIcon />}
            onClick={() => setCopyDialogOpen(true)}
          >
            Zones kopiëren
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreate}>
            Zone toevoegen
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
              Nog geen zones. Voeg een zone toe om te beginnen.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Naam</strong></TableCell>
                <TableCell><strong>Punten</strong></TableCell>
                <TableCell><strong>Locatie</strong></TableCell>
                <TableCell><strong>Openingstijd</strong></TableCell>
                <TableCell><strong>Sluitingstijd</strong></TableCell>
                <TableCell align="right"><strong>Acties</strong></TableCell>
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
                          label="Verborgen"
                          variant="outlined"
                          color="default"
                          title="Verborgen voor teams, triggert nog wel automatiseringen"
                        />
                      )}
                    </Box>
                    {cp.externalId && (
                      <Typography variant="caption" color="text.secondary">
                        Extern: {cp.externalId}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{cp.points}</TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {formatLocation(cp.latitude, cp.longitude)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {cp.zoneType === 'polygon' ? `⬡ Polygoon (${cp.polygonPoints?.length ?? 0} punten)` : `⊙ ${cp.radiusMeters ?? 50}m`}
                    </Typography>
                  </TableCell>
                  <TableCell>{formatDateTime(cp.openingTime)}</TableCell>
                  <TableCell>{formatDateTime(cp.closingTime)}</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => handleEdit(cp)} title="Zone bewerken">
                      <EditIcon />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleDeleteClick(cp)}
                      color="error"
                      title="Zone verwijderen"
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
        <DialogTitle>Zone verwijderen</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Weet je zeker dat je "{checkpointToDelete?.name}" wilt verwijderen? Deze actie kan niet
            ongedaan worden gemaakt.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Annuleren</Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
          >
            {deleting ? <CircularProgress size={20} /> : 'Verwijderen'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Zones;

