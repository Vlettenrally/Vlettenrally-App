/**
 * Participant Map Page - Map view showing own team location and checkpoints
 *
 * Features:
 * - Own team location marker (received via WebSocket LOCATION_UPDATE)
 * - Checkpoint markers for the game
 * - User's device geolocation via MapLibre GeolocateControl
 */
import {lazy, Suspense, useCallback, useEffect, useState} from 'react';
import { useTranslation } from 'react-i18next';
import {Alert, Box, CircularProgress, Typography} from '@mui/material';
import type {MapMarker} from '../../components/MapView';
import {getTeamColor} from '../../utils/teamColors';
import type {TeamLocation} from '../../types/entities';
import {useTeamStore} from '../../store/useTeamStore';
import {useGameStore} from '../../store/useGameStore';
import {useWebSocketSubscription} from '../../hooks/useWebSocket';
import {storage} from '../../services/storage';
import api from '../../services/api';
import {DEFAULT_CENTER, DEFAULT_ZOOM} from '../../utils/mapStyle';

const MapView = lazy(() => import('../../components/MapView'));

export default function ParticipantMap() {
  const { team } = useTeamStore();
  const { t } = useTranslation();
  const gameId = storage.getGameId();
  const teamId = team?.id || storage.getTeamId();

  const { subscribe, connected } = useWebSocketSubscription();

  const [myLocation, setMyLocation] = useState<TeamLocation | null>(null);
  // Checkpoints live in the game store so useTeamSync can refresh them on resume.
  const checkpoints = useGameStore((state) => state.checkpoints);
  const [loading, setLoading] = useState(!!gameId);
  const [error, setError] = useState<string | null>(null);

  // Fetch checkpoints on mount
  useEffect(() => {
    if (!gameId) {
      return;
    }

    let cancelled = false;

    const fetchCheckpoints = async () => {
      try {
        const response = await api.getCheckpoints(gameId);
        if (cancelled) return;

        if (response.data) {
          useGameStore.getState().setCheckpoints(response.data);
        }
        if (response.error) {
          setError(response.error);
        }
      } catch (err) {
        if (cancelled) return;
        console.error('Failed to load checkpoints:', err);
        setError('Kon checkpoints niet laden');
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchCheckpoints();

    return () => { cancelled = true; };
  }, [gameId]);

  // Fetch last known location on mount so the marker appears immediately
  useEffect(() => {
    if (!gameId || !teamId) return;

    let cancelled = false;

    api.getMyLatestLocation(gameId, teamId).then((response) => {
      if (!cancelled && response.data) {
        setMyLocation(response.data);
      }
    });

    return () => { cancelled = true; };
  }, [gameId, teamId]);

  // Subscribe to real-time location updates (team receives only their own location)
  const handleLocationUpdate = useCallback((message: { payload: unknown }) => {
    const payload = message.payload as TeamLocation;
    if (payload && payload.latitude != null && payload.longitude != null) {
      setMyLocation(payload);
    }
  }, []);

  useEffect(() => {
    if (!gameId || !teamId) return;
    return subscribe('LOCATION_UPDATE', handleLocationUpdate);
  }, [gameId, teamId, subscribe, handleLocationUpdate]);

  // Build map markers
  const markers: MapMarker[] = [
    ...(myLocation ? [{
      id: myLocation.teamId,
      lat: myLocation.latitude,
      lng: myLocation.longitude,
      label: myLocation.teamName || team?.name || t('participant.map.myTeam'),
      color: getTeamColor(myLocation.teamId),
      type: 'team' as const,
    }] : []),
    ...checkpoints
      .filter(cp => cp.latitude != null && cp.longitude != null)
      .map(cp => ({
        id: cp.id,
        lat: cp.latitude!,
        lng: cp.longitude!,
        label: cp.name,
        color: cp.color ?? '#ff9800',
        type: 'checkpoint' as const,
        zoneType: cp.zoneType,
        radiusMeters: cp.radiusMeters,
        polygonPoints: cp.polygonPoints,
      })),
  ];

  return (
    <Box sx={{ height: 'calc(100vh - 56px)', display: 'flex', flexDirection: 'column' }}>
      {error && (
        <Alert severity="error" sx={{ borderRadius: 0 }}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1 }}>
          <CircularProgress />
        </Box>
      ) : !gameId ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1 }}>
          <Typography color="text.secondary">{t('participant.map.noGame')}</Typography>
        </Box>
      ) : (
        <Box sx={{ flex: 1, position: 'relative' }}>
          <Suspense fallback={<Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1, height: '100%' }}><CircularProgress /></Box>}>
            {/* Stable initial camera; auto-fit frames checkpoints (or own location) on load. */}
            <MapView
              markers={markers}
              center={DEFAULT_CENTER}
              zoom={DEFAULT_ZOOM}
              followTeamLocation={true}
              teamLocation={myLocation ? [myLocation.longitude, myLocation.latitude] : undefined}
            />
          </Suspense>
          {/* Status indicator */}
          <Box
            sx={{
              position: 'absolute',
              bottom: 16,
              left: 16,
              bgcolor: 'background.paper',
              borderRadius: 1,
              px: 1.5,
              py: 0.5,
              boxShadow: 1,
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
          >
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                bgcolor: connected ? 'success.main' : 'warning.main',
              }}
            />
            <Typography variant="caption" color="text.secondary">
              {myLocation ? t('participant.map.locationAvailable') : t('participant.map.waitingForLocation')}
            </Typography>
          </Box>
        </Box>
      )}
    </Box>
  );
}

