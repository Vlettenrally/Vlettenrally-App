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
  ToggleButtonGroup,
  ToggleButton,
  IconButton,
  FormControlLabel,
  Checkbox,
} from '@mui/material';
import {
  CircleOutlined as CircleIcon,
  HexagonOutlined as PolygonIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useState, useEffect } from 'react';
import api from '../services/api';
import { formatForDateTimeLocalInput } from '../utils/dateTime';
import type { Checkpoint, CheckpointRequest } from '../types/entities';
import LocationPickerMap from './LocationPickerMap';

interface ZoneFormDialogProps {
  open: boolean;
  onClose: () => void;
  checkpoint: Checkpoint | null;
  gameId: string;
  onSave?: (checkpoint: Checkpoint) => void;
}

/** Compute the centroid of polygon vertices */
function computeCentroid(points: number[][]): { lat: number; lng: number } {
  const n = points.length;
  if (n === 0) return { lat: 0, lng: 0 };
  const sumLat = points.reduce((s, p) => s + p[0], 0);
  const sumLng = points.reduce((s, p) => s + p[1], 0);
  return { lat: sumLat / n, lng: sumLng / n };
}

/** Round a coordinate to 5 decimal places (~1m precision) */
function roundCoord(v: number): number {
  return Math.round(v * 100000) / 100000;
}

/** Parse a coordinate string, accepting both comma and dot as decimal separator */
function parseCoord(raw: string): number | undefined {
  if (raw === '' || raw === '-') return undefined;
  const normalized = raw.replace(',', '.');
  const val = parseFloat(normalized);
  return isNaN(val) ? undefined : val;
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

  const [formData, setFormData] = useState<Omit<CheckpointRequest, 'points'> & { points?: number }>({
    name: '',
    openingTime: '',
    closingTime: '',
    points: 0,
    latitude: undefined,
    longitude: undefined,
    zoneType: 'circle',
    radiusMeters: 50,
    polygonPoints: undefined,
    hidden: false,
  });

  const isCreateMode = !checkpoint;

  useEffect(() => {
    if (checkpoint) {
      setFormData({
        name: checkpoint.name,
        openingTime: checkpoint.openingTime ? formatForDateTimeLocalInput(checkpoint.openingTime) : '',
        closingTime: checkpoint.closingTime ? formatForDateTimeLocalInput(checkpoint.closingTime) : '',
        points: checkpoint.points,
        latitude: checkpoint.latitude,
        longitude: checkpoint.longitude,
        zoneType: checkpoint.zoneType || 'circle',
        radiusMeters: checkpoint.radiusMeters ?? 50,
        polygonPoints: checkpoint.polygonPoints,
        hidden: checkpoint.hidden ?? false,
      });
    } else {
      setFormData({
        name: '',
        openingTime: '',
        closingTime: '',
        points: 0,
        latitude: undefined,
        longitude: undefined,
        zoneType: 'circle',
        radiusMeters: 50,
        polygonPoints: undefined,
        hidden: false,
      });
    }
    setError(null);
  }, [checkpoint, open]);

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

    if (formData.zoneType === 'polygon' && (!formData.polygonPoints || formData.polygonPoints.length < 3)) {
      setError('Een polygoon heeft minimaal 3 punten nodig');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const requestData: CheckpointRequest = {
        name: formData.name.trim(),
        points: formData.points ?? 0,
        latitude: formData.latitude || undefined,
        longitude: formData.longitude || undefined,
        openingTime: formatToISOString(formData.openingTime || ''),
        closingTime: formatToISOString(formData.closingTime || ''),
        zoneType: formData.zoneType,
        radiusMeters: formData.zoneType === 'circle' ? (formData.radiusMeters ?? 50) : undefined,
        polygonPoints: formData.zoneType === 'polygon' ? formData.polygonPoints : undefined,
        hidden: formData.hidden ?? false,
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

  /** Update a single polygon vertex coordinate */
  const handlePolygonPointChange = (index: number, field: 'lat' | 'lng', rawValue: string) => {
    const val = parseCoord(rawValue);
    if (val === undefined) return;
    const pts = [...(formData.polygonPoints || [])];
    const point = [...(pts[index] || [0, 0])];
    point[field === 'lat' ? 0 : 1] = val;
    pts[index] = point;
    const centroid = computeCentroid(pts);
    setFormData(prev => ({ ...prev, polygonPoints: pts, latitude: centroid.lat, longitude: centroid.lng }));
  };

  /** Add a new polygon vertex at the midpoint of the longest edge */
  const handleAddPolygonPoint = () => {
    const pts = [...(formData.polygonPoints || [])];

    if (pts.length < 2) {
      // Not enough points to find an edge — just add near center/last point
      const basePt = pts.length > 0 ? pts[pts.length - 1] : [formData.latitude ?? 52.37, formData.longitude ?? 4.90];
      pts.push([roundCoord(basePt[0] + 0.0005), roundCoord(basePt[1] + 0.0005)]);
    } else {
      // Find the longest edge and insert a midpoint there
      let longestIdx = 0;
      let longestDist = -1;
      for (let i = 0; i < pts.length; i++) {
        const a = pts[i];
        const b = pts[(i + 1) % pts.length];
        const d = (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2;
        if (d > longestDist) {
          longestDist = d;
          longestIdx = i;
        }
      }
      const a = pts[longestIdx];
      const b = pts[(longestIdx + 1) % pts.length];
      const mid = [roundCoord((a[0] + b[0]) / 2), roundCoord((a[1] + b[1]) / 2)];
      pts.splice(longestIdx + 1, 0, mid);
    }

    const centroid = computeCentroid(pts);
    setFormData(prev => ({ ...prev, polygonPoints: pts, latitude: centroid.lat, longitude: centroid.lng }));
  };

  /** Remove a polygon vertex */
  const handleRemovePolygonPoint = (index: number) => {
    const pts = [...(formData.polygonPoints || [])];
    pts.splice(index, 1);
    const centroid = pts.length > 0 ? computeCentroid(pts) : { lat: formData.latitude ?? 0, lng: formData.longitude ?? 0 };
    setFormData(prev => ({
      ...prev,
      polygonPoints: pts.length > 0 ? pts : undefined,
      latitude: pts.length > 0 ? centroid.lat : prev.latitude,
      longitude: pts.length > 0 ? centroid.lng : prev.longitude,
    }));
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
          value={formData.points ?? ''}
          onChange={(e) => {
            const raw = e.target.value;
            setFormData({ ...formData, points: raw === '' ? undefined : parseInt(raw) });
          }}
          margin="normal"
        />

        {/* Zone type & radius config */}
        <Typography variant="subtitle2" sx={{ mt: 2, mb: 0.5 }}>
          Zone-vorm
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <ToggleButtonGroup
            value={formData.zoneType}
            exclusive
            onChange={(_, val) => { if (val) setFormData({ ...formData, zoneType: val }); }}
            size="small"
          >
            <ToggleButton value="circle">
              <CircleIcon sx={{ mr: 0.5 }} fontSize="small" /> Cirkel
            </ToggleButton>
            <ToggleButton value="polygon">
              <PolygonIcon sx={{ mr: 0.5 }} fontSize="small" /> Polygoon
            </ToggleButton>
          </ToggleButtonGroup>

          {formData.zoneType === 'circle' && (
            <TextField
              label="Straal (m)"
              type="number"
              size="small"
              value={formData.radiusMeters ?? ''}
              onChange={(e) => {
                const raw = e.target.value.replace(',', '.');
                setFormData({ ...formData, radiusMeters: raw === '' ? undefined : parseFloat(raw) });
              }}
              slotProps={{ htmlInput: { min: 1, step: 1 } }}
              sx={{ width: 130 }}
            />
          )}
        </Box>

        {formData.zoneType === 'polygon' && (
          <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
            Klik op de kaart om punten toe te voegen (min. 3). Punten kunnen ook handmatig worden aangepast.
          </Typography>
        )}

        <Typography variant="subtitle2" sx={{ mt: 1, mb: 0.5 }}>
          Locatie
        </Typography>
        <LocationPickerMap
          value={
            formData.latitude != null && formData.longitude != null
              ? { lat: formData.latitude, lng: formData.longitude }
              : null
          }
          onChange={(lat, lng) => setFormData(prev => ({ ...prev, latitude: roundCoord(lat), longitude: roundCoord(lng) }))}
          height={isMobile ? 250 : 350}
          zoneType={formData.zoneType as 'circle' | 'polygon'}
          radiusMeters={formData.radiusMeters}
          polygonPoints={formData.polygonPoints}
          onPolygonPointsChange={(points) => {
            const rounded = points.map(p => [roundCoord(p[0]), roundCoord(p[1])]);
            const centroid = rounded.length > 0 ? computeCentroid(rounded) : { lat: formData.latitude ?? 0, lng: formData.longitude ?? 0 };
            setFormData(prev => ({
              ...prev,
              polygonPoints: rounded.length > 0 ? rounded : undefined,
              latitude: rounded.length > 0 ? centroid.lat : prev.latitude,
              longitude: rounded.length > 0 ? centroid.lng : prev.longitude,
            }));
          }}
        />

        {/* Editable coordinate fields */}
        {formData.zoneType === 'circle' && (
          <Box sx={{ mt: 1 }}>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
              Coördinaten
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <TextField
                label="Breedtegraad"
                type="number"
                size="small"
                fullWidth
                lang="en"
                value={formData.latitude ?? ''}
                onChange={(e) => {
                  setFormData(prev => ({ ...prev, latitude: parseCoord(e.target.value) }));
                }}
                slotProps={{ htmlInput: { step: 0.00001, lang: 'en' } }}
              />
              <TextField
                label="Lengtegraad"
                type="number"
                size="small"
                fullWidth
                lang="en"
                value={formData.longitude ?? ''}
                onChange={(e) => {
                  setFormData(prev => ({ ...prev, longitude: parseCoord(e.target.value) }));
                }}
                slotProps={{ htmlInput: { step: 0.00001, lang: 'en' } }}
              />
            </Box>
          </Box>
        )}

        {formData.zoneType === 'polygon' && (
          <Box sx={{ mt: 1 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
              <Typography variant="subtitle2">
                Polygoon-punten ({formData.polygonPoints?.length ?? 0})
              </Typography>
              <Button size="small" startIcon={<AddIcon />} onClick={handleAddPolygonPoint}>
                Punt toevoegen
              </Button>
            </Box>
            {(formData.polygonPoints || []).map((point, index) => (
              <Box key={index} sx={{ display: 'flex', gap: 1, mb: 0.5, alignItems: 'center' }}>
                <Typography variant="caption" sx={{ width: 24, textAlign: 'right', color: 'text.secondary' }}>
                  {index + 1}.
                </Typography>
                <TextField
                  label="Breedtegraad"
                  type="number"
                  size="small"
                  lang="en"
                  value={point[0]}
                  onChange={(e) => handlePolygonPointChange(index, 'lat', e.target.value)}
                  slotProps={{ htmlInput: { step: 0.00001, lang: 'en' } }}
                  sx={{ flex: 1 }}
                />
                <TextField
                  label="Lengtegraad"
                  type="number"
                  size="small"
                  lang="en"
                  value={point[1]}
                  onChange={(e) => handlePolygonPointChange(index, 'lng', e.target.value)}
                  slotProps={{ htmlInput: { step: 0.00001, lang: 'en' } }}
                  sx={{ flex: 1 }}
                />
                <IconButton size="small" color="error" onClick={() => handleRemovePolygonPoint(index)} title="Punt verwijderen">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Box>
            ))}
          </Box>
        )}

        <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
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

        <FormControlLabel
          sx={{ mt: 1 }}
          control={
            <Checkbox
              checked={formData.hidden ?? false}
              onChange={(e) => setFormData(prev => ({ ...prev, hidden: e.target.checked }))}
            />
          }
          label="Verborgen voor teams (triggert nog wel automatiseringen)"
        />
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
