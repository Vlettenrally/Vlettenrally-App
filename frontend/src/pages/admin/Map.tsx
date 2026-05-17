/**
 * Admin Map Page - Full-screen map view for monitoring team locations and checkpoints
 *
 * Features:
 * - Real-time team location tracking via WebSocket LOCATION_UPDATE events
 * - Checkpoint markers
 * - MAP_HEARTBEAT sent every 10s so backend streams location data to this session
 * - Initial data loaded via REST API on mount
 */
import {lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, Box, ButtonBase, CircularProgress, Typography} from '@mui/material';
import type {MapMarker, MapTrack} from '../../components/MapView';
import {getTeamColor} from '../../utils/teamColors';
import type {Checkpoint, TeamLocation} from '../../types/entities';
import {useGameStore} from '../../store/useGameStore';
import {useAdminWebSocket} from '../../hooks/useWebSocket';
import api from '../../services/api';
import MapLegendSidebar, {type TrackWindowMinutes} from './MapLegendSidebar';

const MapView = lazy(() => import('../../components/MapView'));

const MAP_HEARTBEAT_INTERVAL_MS = 10_000; // 10 seconds

export default function AdminMap() {
  const { selectedGameId } = useGameStore();
  const { subscribe, send, connected } = useAdminWebSocket(selectedGameId);

  const [teamLocations, setTeamLocations] = useState<TeamLocation[]>([]);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [loading, setLoading] = useState(!!selectedGameId);
  const [error, setError] = useState<string | null>(null);

  // Legend filter state — resets on page reload (no persistence by design).
  const [hiddenTeamIds, setHiddenTeamIds] = useState<Set<string>>(() => new Set());
  const [hiddenCheckpointIds, setHiddenCheckpointIds] = useState<Set<string>>(() => new Set());
  const [trackTeamIds, setTrackTeamIds] = useState<Set<string>>(() => new Set());
  const [windowMinutes, setWindowMinutes] = useState<TrackWindowMinutes>(60);
  const [teamTracks, setTeamTracks] = useState<Record<string, Array<[number, number]>>>({});
  const [legendOpen, setLegendOpen] = useState(false);
  const [legendPinned, setLegendPinned] = useState(false);

  // Drop stale history responses if the user changed the window or untoggled the team while the fetch was in flight.
  const trackRequestIdRef = useRef(0);
  const trackTeamIdsRef = useRef(trackTeamIds);
  trackTeamIdsRef.current = trackTeamIds;
  const windowMinutesRef = useRef(windowMinutes);
  windowMinutesRef.current = windowMinutes;

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
      return;
    }

    let cancelled = false;

    const fetchData = async () => {
      try {
        const [locationsResponse, checkpointsResponse] = await Promise.all([
          api.getLatestTeamLocations(selectedGameId),
          api.getCheckpoints(selectedGameId),
        ]);
        if (cancelled) return;

        if (locationsResponse.data) {
          setTeamLocations(locationsResponse.data);
        }
        if (checkpointsResponse.data) {
          setCheckpoints(checkpointsResponse.data);
        }
        if (locationsResponse.error) {
          setError(locationsResponse.error);
        }
      } catch (err) {
        if (cancelled) return;
        console.error('Failed to load map data:', err);
        setError('Kon kaartdata niet laden');
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchData();

    return () => { cancelled = true; };
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

      // Extend the polyline for this team if it is currently tracked.
      if (trackTeamIdsRef.current.has(teamLoc.teamId)) {
        setTeamTracks(prev => {
          const existing = prev[teamLoc.teamId];
          if (!existing) return prev;
          const last = existing[existing.length - 1];
          if (last && last[0] === teamLoc.longitude && last[1] === teamLoc.latitude) {
            return prev;
          }
          return {
            ...prev,
            [teamLoc.teamId]: [...existing, [teamLoc.longitude, teamLoc.latitude]],
          };
        });
      }
    }
  }, []);

  useEffect(() => {
    if (!selectedGameId) return;
    return subscribe('LOCATION_UPDATE', handleLocationUpdate);
  }, [selectedGameId, subscribe, handleLocationUpdate]);

  // Build map markers (apply legend visibility filters)
  const markers: MapMarker[] = [
    ...teamLocations
      .filter(loc => !hiddenTeamIds.has(loc.teamId))
      .map(loc => ({
        id: loc.teamId,
        lat: loc.latitude,
        lng: loc.longitude,
        label: loc.teamName,
        color: getTeamColor(loc.teamId),
        type: 'team' as const,
      })),
    ...checkpoints
      .filter(cp => cp.latitude != null && cp.longitude != null)
      .filter(cp => !hiddenCheckpointIds.has(cp.id))
      .map(cp => ({
        id: cp.id,
        lat: cp.latitude!,
        lng: cp.longitude!,
        label: cp.name,
        color: '#ff9800',
        type: 'checkpoint' as const,
        zoneType: cp.zoneType,
        radiusMeters: cp.radiusMeters,
        polygonPoints: cp.polygonPoints,
      })),
  ];

  const tracks: MapTrack[] = useMemo(
    () => Object.entries(teamTracks).map(([teamId, points]) => ({
      teamId,
      color: getTeamColor(teamId),
      points,
    })),
    [teamTracks],
  );

  const legendTeams = useMemo(
    () => teamLocations.map(loc => ({
      id: loc.teamId,
      name: loc.teamName,
      color: getTeamColor(loc.teamId),
    })),
    [teamLocations],
  );

  const legendCheckpoints = useMemo(
    () => checkpoints.map(cp => ({ id: cp.id, name: cp.name })),
    [checkpoints],
  );

  // Fetch (or refetch) history for every currently-tracked team whenever the set or window changes.
  useEffect(() => {
    if (!selectedGameId) return;
    if (trackTeamIds.size === 0) {
      setTeamTracks({});
      return;
    }
    const requestId = ++trackRequestIdRef.current;
    const ids = Array.from(trackTeamIds);
    let cancelled = false;

    (async () => {
      const results = await Promise.all(
        ids.map(async teamId => {
          try {
            const resp = await api.getTeamLocationHistory(selectedGameId, teamId, windowMinutes);
            return { teamId, points: resp.data ?? [] };
          } catch (err) {
            console.error('Failed to load track for team', teamId, err);
            return { teamId, points: [] as TeamLocation[] };
          }
        }),
      );
      if (cancelled || requestId !== trackRequestIdRef.current) return;

      const next: Record<string, Array<[number, number]>> = {};
      for (const { teamId, points } of results) {
        next[teamId] = points.map(p => [p.longitude, p.latitude] as [number, number]);
      }
      setTeamTracks(next);
    })();

    return () => { cancelled = true; };
  }, [trackTeamIds, windowMinutes, selectedGameId]);

  const handleToggleTeam = useCallback((id: string) => {
    setHiddenTeamIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const handleToggleCheckpoint = useCallback((id: string) => {
    setHiddenCheckpointIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const handleToggleTrack = useCallback((id: string) => {
    setTrackTeamIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const handleSetTeamsHidden = useCallback((ids: string[], hidden: boolean) => {
    setHiddenTeamIds(prev => {
      const next = new Set(prev);
      for (const id of ids) {
        if (hidden) next.add(id);
        else next.delete(id);
      }
      return next;
    });
  }, []);

  const handleSetCheckpointsHidden = useCallback((ids: string[], hidden: boolean) => {
    setHiddenCheckpointIds(prev => {
      const next = new Set(prev);
      for (const id of ids) {
        if (hidden) next.add(id);
        else next.delete(id);
      }
      return next;
    });
  }, []);

  // Esc closes the sidebar when not pinned.
  useEffect(() => {
    if (!legendOpen || legendPinned) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setLegendOpen(false);
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [legendOpen, legendPinned]);

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
        <Box
          sx={{ flex: 1, position: 'relative' }}
          onMouseDown={() => {
            if (legendOpen && !legendPinned) setLegendOpen(false);
          }}
        >
          <Suspense fallback={<Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1, height: '100%' }}><CircularProgress /></Box>}>
            <MapView
              markers={markers}
              tracks={tracks}
              zoom={10}
            />
          </Suspense>
          {/* Legend / filter trigger */}
          <ButtonBase
            onMouseDown={(e) => e.stopPropagation()}
            onClick={() => setLegendOpen(o => !o)}
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
              cursor: 'pointer',
              '&:hover': { bgcolor: 'action.hover' },
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
          </ButtonBase>
          <MapLegendSidebar
            open={legendOpen}
            pinned={legendPinned}
            onClose={() => setLegendOpen(false)}
            onTogglePin={() => setLegendPinned(p => !p)}
            teams={legendTeams}
            checkpoints={legendCheckpoints}
            hiddenTeamIds={hiddenTeamIds}
            hiddenCheckpointIds={hiddenCheckpointIds}
            trackTeamIds={trackTeamIds}
            windowMinutes={windowMinutes}
            onToggleTeam={handleToggleTeam}
            onToggleCheckpoint={handleToggleCheckpoint}
            onToggleTrack={handleToggleTrack}
            onSetTeamsHidden={handleSetTeamsHidden}
            onSetCheckpointsHidden={handleSetCheckpointsHidden}
            onWindowChange={setWindowMinutes}
          />
        </Box>
      )}
    </Box>
  );
}

