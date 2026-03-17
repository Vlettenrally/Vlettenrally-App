/**
 * Participant Map Page - Map view showing own team location and checkpoints
 *
 * Features:
 * - Own team location marker (received via WebSocket LOCATION_UPDATE)
 * - Checkpoint markers for the game
 * - User's device geolocation via MapLibre GeolocateControl
 */
import { useState, useEffect, useCallback } from 'react';
import { Box, Alert, CircularProgress, Typography } from '@mui/material';
import { TeamLayout } from '../../components/TeamLayout';
import { MapView } from '../../components/MapView';
import type { MapMarker } from '../../components/MapView';
import { getTeamColor } from '../../utils/teamColors';
import type { TeamLocation, Checkpoint } from '../../types/entities';
import { useTeamStore } from '../../store/useTeamStore';
import { useTeamWebSocket } from '../../hooks/useWebSocket';
import { storage } from '../../services/storage';
import api from '../../services/api';

export default function ParticipantMap() {
  const { team } = useTeamStore();
  const gameId = storage.getGameId();
  const teamId = team?.id || storage.getTeamId();

  const { subscribe, connected } = useTeamWebSocket(gameId, teamId || null);

  const [myLocation, setMyLocation] = useState<TeamLocation | null>(null);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
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
          setCheckpoints(response.data);
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

  // Subscribe to real-time location updates (team receives only their own location)
  const handleLocationUpdate = useCallback((message: { payload: unknown }) => {
    const payload = message.payload as TeamLocation;
    if (payload && payload.latitude && payload.longitude) {
      setMyLocation(payload);
    }
  }, []);

  useEffect(() => {
    if (!gameId || !teamId) return;
    const unsubscribe = subscribe('LOCATION_UPDATE', handleLocationUpdate);
    return unsubscribe;
  }, [gameId, teamId, subscribe, handleLocationUpdate]);

  // Build map markers
  const markers: MapMarker[] = [
    ...(myLocation ? [{
      id: myLocation.teamId,
      lat: myLocation.latitude,
      lng: myLocation.longitude,
      label: myLocation.teamName || team?.name || 'Mijn team',
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
        color: '#ff9800',
        type: 'checkpoint' as const,
      })),
  ];

  // Center on team location if available, otherwise center of Netherlands
  const center: [number, number] = myLocation
    ? [myLocation.longitude, myLocation.latitude]
    : [5.2913, 52.1326];

  return (
    <TeamLayout>
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
            <Typography color="text.secondary">Geen game geselecteerd</Typography>
          </Box>
        ) : (
          <Box sx={{ flex: 1, position: 'relative' }}>
            <MapView
              markers={markers}
              center={center}
              zoom={myLocation ? 14 : 8}
            />
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
                {myLocation ? 'Locatie beschikbaar' : 'Wachten op locatie...'}
              </Typography>
            </Box>
          </Box>
        )}
      </Box>
    </TeamLayout>
  );
}

