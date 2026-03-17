/**
 * Admin Map Page - Full-screen map view for monitoring team locations and checkpoints
 *
 * Features:
 * - Real-time team location tracking via WebSocket LOCATION_UPDATE events
 * - Checkpoint markers
 * - MAP_HEARTBEAT sent every 10s so backend streams location data to this session
 * - Initial data loaded via REST API on mount
 */
import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Alert, CircularProgress, Typography } from '@mui/material';
import { MapView, getTeamColor } from '../../components/MapView';
import type { MapMarker } from '../../components/MapView';
import type { TeamLocation, Checkpoint } from '../../types/entities';
import { useGameStore } from '../../store/useGameStore';
import { useAdminWebSocket } from '../../hooks/useWebSocket';
import api from '../../services/api';

const MAP_HEARTBEAT_INTERVAL_MS = 10_000; // 10 seconds

export default function AdminMap() {
  const { selectedGameId } = useGameStore();
  const { subscribe, send, connected } = useAdminWebSocket(selectedGameId);

  const [teamLocations, setTeamLocations] = useState<TeamLocation[]>([]);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Send MAP_HEARTBEAT to backend so it streams location updates to this session
  useEffect(() => {
    if (!connected) return;

    // Send initial heartbeat
    send({ type: 'MAP_HEARTBEAT' });

    // Send periodic heartbeats
    heartbeatRef.current = setInterval(() => {
      send({ type: 'MAP_HEARTBEAT' });
    }, MAP_HEARTBEAT_INTERVAL_MS);

    return () => {
      if (heartbeatRef.current) {
        clearInterval(heartbeatRef.current);
        heartbeatRef.current = null;
      }
    };
  }, [connected, send]);

  // Fetch initial data when game changes
  useEffect(() => {
    if (!selectedGameId) {
      setTeamLocations([]);
      setCheckpoints([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    Promise.all([
      api.getLatestTeamLocations(selectedGameId),
      api.getCheckpoints(selectedGameId),
    ]).then(([locationsResponse, checkpointsResponse]) => {
      if (locationsResponse.data) {
        setTeamLocations(locationsResponse.data);
      }
      if (checkpointsResponse.data) {
        setCheckpoints(checkpointsResponse.data);
      }
      if (locationsResponse.error) {
        setError(locationsResponse.error);
      }
    }).catch(err => {
      console.error('Failed to load map data:', err);
      setError('Kon kaartdata niet laden');
    }).finally(() => {
      setLoading(false);
    });
  }, [selectedGameId]);

  // Subscribe to real-time location updates
  const handleLocationUpdate = useCallback((message: { payload: unknown }) => {
    const payload = message.payload;

    if (Array.isArray(payload)) {
      // Admin receives full game locations as array
      setTeamLocations(payload as TeamLocation[]);
    } else {
      // Single team update - merge into existing locations
      const teamLoc = payload as TeamLocation;
      setTeamLocations(prev => {
        const updated = prev.filter(l => l.teamId !== teamLoc.teamId);
        return [...updated, teamLoc];
      });
    }
  }, []);

  useEffect(() => {
    if (!selectedGameId) return;
    const unsubscribe = subscribe('LOCATION_UPDATE', handleLocationUpdate);
    return unsubscribe;
  }, [selectedGameId, subscribe, handleLocationUpdate]);

  // Build map markers
  const markers: MapMarker[] = [
    ...teamLocations.map(loc => ({
      id: loc.teamId,
      lat: loc.latitude,
      lng: loc.longitude,
      label: loc.teamName,
      color: getTeamColor(loc.teamId),
      type: 'team' as const,
    })),
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

  return (
    <Box sx={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      {error && (
        <Alert severity="error" sx={{ borderRadius: 0 }}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1 }}>
          <CircularProgress />
        </Box>
      ) : !selectedGameId ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1 }}>
          <Typography color="text.secondary">Selecteer een game om de kaart te bekijken</Typography>
        </Box>
      ) : (
        <Box sx={{ flex: 1, position: 'relative' }}>
          <MapView
            markers={markers}
            zoom={10}
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
                bgcolor: connected ? 'success.main' : 'error.main',
              }}
            />
            <Typography variant="caption" color="text.secondary">
              {teamLocations.length} teams · {checkpoints.length} checkpoints
            </Typography>
          </Box>
        </Box>
      )}
    </Box>
  );
}

