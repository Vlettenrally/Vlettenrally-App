import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  CircularProgress,
  Alert,
} from '@mui/material';
import { useState, useEffect } from 'react';
import api from '../services/api';
import type { Checkpoint, CheckpointRequest } from '../types/entities';

interface ZoneFormDialogProps {
  open: boolean;
  onClose: () => void;
  checkpoint: Checkpoint | null;
  gameId: string;
  onSave?: (checkpoint: Checkpoint) => void;
}

export default function ZoneFormDialog({
  open,
  onClose,
  checkpoint,
  gameId,
  onSave,
}: ZoneFormDialogProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [formData, setFormData] = useState<CheckpointRequest>({
    name: '',
    openingTime: '',
    closingTime: '',
    points: 0,
    latitude: undefined,
    longitude: undefined,
  });

  const isCreateMode = !checkpoint;

  useEffect(() => {
    if (checkpoint) {
      setFormData({
        name: checkpoint.name,
        openingTime: checkpoint.openingTime ? formatDateTimeLocal(checkpoint.openingTime) : '',
        closingTime: checkpoint.closingTime ? formatDateTimeLocal(checkpoint.closingTime) : '',
        points: checkpoint.points,
        latitude: checkpoint.latitude,
        longitude: checkpoint.longitude,
      });
    } else {
      setFormData({
        name: '',
        openingTime: '',
        closingTime: '',
        points: 0,
        latitude: undefined,
        longitude: undefined,
      });
    }
    setError(null);
  }, [checkpoint, open]);

  const formatDateTimeLocal = (dateStr: string): string => {
    try {
      return new Date(dateStr).toISOString().slice(0, 16);
    } catch {
      return '';
    }
  };

  const formatToISOString = (localDateTime: string): string | undefined => {
    if (!localDateTime) return undefined;
    try {
      return new Date(localDateTime).toISOString();
    } catch {
      return undefined;
    }
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      setError('Naam is verplicht');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const requestData: CheckpointRequest = {
        name: formData.name.trim(),
        points: formData.points,
        latitude: formData.latitude || undefined,
        longitude: formData.longitude || undefined,
        openingTime: formatToISOString(formData.openingTime || ''),
        closingTime: formatToISOString(formData.closingTime || ''),
      };

      let response;
      if (isCreateMode) {
        response = await api.createCheckpoint(gameId, requestData);
      } else {
        response = await api.updateCheckpoint(gameId, checkpoint!.id, requestData);
      }

      if (response.error) {
        setError(response.error);
        return;
      }

      if (onSave && response.data) {
        onSave(response.data);
      }
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Opslaan mislukt');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{isCreateMode ? 'Zone toevoegen' : 'Zone bewerken'}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2, mt: 1 }}>
            {error}
          </Alert>
        )}

        <TextField
          label="Naam"
          fullWidth
          required
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          margin="normal"
          autoFocus
        />

        <TextField
          label="Punten"
          fullWidth
          type="number"
          value={formData.points}
          onChange={(e) => setFormData({ ...formData, points: parseInt(e.target.value) || 0 })}
          margin="normal"
        />

        <Box sx={{ display: 'flex', gap: 2 }}>
          <TextField
            label="Breedtegraad"
            fullWidth
            type="number"
            slotProps={{ htmlInput: { step: 'any' } }}
            value={formData.latitude ?? ''}
            onChange={(e) =>
              setFormData({ ...formData, latitude: e.target.value ? parseFloat(e.target.value) : undefined })
            }
            margin="normal"
          />
          <TextField
            label="Lengtegraad"
            fullWidth
            type="number"
            slotProps={{ htmlInput: { step: 'any' } }}
            value={formData.longitude ?? ''}
            onChange={(e) =>
              setFormData({ ...formData, longitude: e.target.value ? parseFloat(e.target.value) : undefined })
            }
            margin="normal"
          />
        </Box>

        <Box sx={{ display: 'flex', gap: 2 }}>
          <TextField
            label="Openingstijd"
            fullWidth
            type="datetime-local"
            value={formData.openingTime || ''}
            onChange={(e) => setFormData({ ...formData, openingTime: e.target.value })}
            margin="normal"
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Sluitingstijd"
            fullWidth
            type="datetime-local"
            value={formData.closingTime || ''}
            onChange={(e) => setFormData({ ...formData, closingTime: e.target.value })}
            margin="normal"
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          Annuleren
        </Button>
        <Button onClick={handleSave} variant="contained" disabled={loading}>
          {loading ? <CircularProgress size={20} /> : isCreateMode ? 'Toevoegen' : 'Opslaan'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}



