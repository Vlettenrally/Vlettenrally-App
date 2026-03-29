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
  Typography,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import { useState, useEffect } from 'react';
import api from '../services/api';
import type { Checkpoint, CheckpointRequest } from '../types/entities';
import LocationPickerMap from './LocationPickerMap';

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
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

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
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth fullScreen={isMobile}>
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

        <Typography variant="subtitle2" sx={{ mt: 2, mb: 0.5 }}>
          Locatie
        </Typography>
        <LocationPickerMap
          value={
            formData.latitude != null && formData.longitude != null
              ? { lat: formData.latitude, lng: formData.longitude }
              : null
          }
          onChange={(lat, lng) => setFormData({ ...formData, latitude: lat, longitude: lng })}
          height={isMobile ? 250 : 350}
        />

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



