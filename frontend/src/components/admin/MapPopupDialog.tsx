import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, IconButton } from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import MapView, { type MapMarker } from '../MapView';
import type { Checkpoint } from '../../types/entities';

interface MapPopupDialogProps {
  open: boolean;
  onClose: () => void;
  /** WGS84 latitude */
  lat: number;
  /** WGS84 longitude */
  lng: number;
  /** Label shown above the marker */
  label: string;
  color?: string;
  /** Optional checkpoint set to render zone shapes alongside the point. */
  checkpoints?: Checkpoint[];
}

/**
 * Modal that renders a single map marker on top of the project's MapLibre map.
 * Used by the Points Management page to pinpoint where a ledger entry happened.
 */
export default function MapPopupDialog({ open, onClose, lat, lng, label, color = '#1976d2', checkpoints = [] }: MapPopupDialogProps) {
  const { t } = useTranslation();
  const markers = useMemo<MapMarker[]>(() => {
    const teamMarker: MapMarker = { id: 'point-entry', lat, lng, label, color, type: 'team' };
    const checkpointMarkers: MapMarker[] = checkpoints
      .filter((cp) => cp.latitude != null && cp.longitude != null)
      .map((cp) => ({
        id: cp.id,
        lat: cp.latitude as number,
        lng: cp.longitude as number,
        label: cp.name,
        color: '#ff9800',
        type: 'checkpoint',
        zoneType: cp.zoneType,
        radiusMeters: cp.radiusMeters,
        polygonPoints: cp.polygonPoints,
      }));
    return [teamMarker, ...checkpointMarkers];
  }, [lat, lng, label, color, checkpoints]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
        {t('components.mapPopup.title')}
        <IconButton onClick={onClose} size="small" aria-label={t('components.mapPopup.close')}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent sx={{ p: 0 }}>
        <Box sx={{ height: 500 }}>
          <MapView
            markers={markers}
            center={[lng, lat]}
            zoom={15}
            markerStyle="pin"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('components.mapPopup.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
