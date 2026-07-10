/**
 * Admin Map Page - Full-screen map view for monitoring team locations and checkpoints
 *
 * Features:
 * - Real-time team location tracking via WebSocket LOCATION_UPDATE events
 * - Checkpoint markers
 * - ADMIN_HEARTBEAT (topic "map") sent every 10s so backend streams location data to this session
 * - Initial data loaded via REST API on mount
 */
import {lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, Box, ButtonBase, CircularProgress, Typography} from '@mui/material';
import type {MapMarker, MapTrack, MapViewHandle} from '../../components/MapView';
import type {Checkpoint, TeamLocation} from '../../types/entities';
import {useGameStore} from '../../store/useGameStore';
import {useWebSocketSubscription} from '../../hooks/useWebSocket';
import {useAdminResync} from '../../hooks/useAdminSync';
import {useAdminTopicHeartbeat, ADMIN_TOPICS} from '../../hooks/useAdminHeartbeat';
import api from '../../services/api';
import MapLegendSidebar, {type TrackWindowMinutes} from './MapLegendSidebar';
import {STALE_AFTER_MS, TEAM_COLOR} from './mapConstants';
import {appendTrackPoints, mergeLocations, partitionTrackedUpdates, toTrackPoints} from './trackUtils';

const MapView = lazy(() => import('../../components/MapView'));

// Re-evaluate marker staleness on this cadence even when no new data arrives.
const STALENESS_TICK_MS = 30_000; // 30 seconds

export default function AdminMap() {
  const { selectedGameId } = useGameStore();
  const { subscribe, connected } = useWebSocketSubscription();

  const [teamLocations, setTeamLocations] = useState<TeamLocation[]>([]);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [loading, setLoading] = useState(!!selectedGameId);
  const [error, setError] = useState<string | null>(null);
  // Ticks periodically so marker staleness is recomputed without new location data.
  const [now, setNow] = useState(() => Date.now());

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
  // Latest locations, kept in a ref so the WebSocket handler can compare a new fix's
  // timestamp against the previously-known one without re-subscribing on every update.
  const teamLocationsRef = useRef(teamLocations);
  teamLocationsRef.current = teamLocations;

  const mapRef = useRef<MapViewHandle>(null);

  // Heartbeat so the backend streams location updates to this session only while the map is open.
  useAdminTopicHeartbeat(ADMIN_TOPICS.MAP);

  // Periodically advance `now` so stale markers dim without waiting for new data.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), STALENESS_TICK_MS);
    return () => clearInterval(id);
  }, []);

  // Fetch the latest location/checkpoint snapshot. Used on game change and on
  // resync (app resume / reconnect) to top up state that missed WebSocket events.
  const fetchData = useCallback(async () => {
    if (!selectedGameId) return;
    try {
      const [locationsResponse, checkpointsResponse] = await Promise.all([
        api.getLatestTeamLocations(selectedGameId),
        api.getCheckpoints(selectedGameId),
      ]);

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
      console.error('Failed to load map data:', err);
      setError('Kon kaartdata niet laden');
    } finally {
      setLoading(false);
    }
  }, [selectedGameId]);

  // Fetch initial data when game changes
  useEffect(() => {
    if (!selectedGameId) return;
    void fetchData();
  }, [selectedGameId, fetchData]);

  useAdminResync(fetchData);

  // Refetch the full history for specific tracked teams and merge it into the tracks,
  // replacing only those teams' polylines. Used when a live gap means appending would
  // skip points. Responses are dropped if the team was untoggled or the window changed
  // while in flight.
  const refetchTracks = useCallback((ids: string[]) => {
    if (!selectedGameId || ids.length === 0) return;
    const windowAtRequest = windowMinutesRef.current;
    for (const teamId of ids) {
      void (async () => {
        try {
          const resp = await api.getTeamLocationHistory(selectedGameId, teamId, windowAtRequest);
          if (!trackTeamIdsRef.current.has(teamId) || windowMinutesRef.current !== windowAtRequest) return;
          setTeamTracks(prev => ({ ...prev, [teamId]: toTrackPoints(resp.data ?? []) }));
        } catch (err) {
          console.error('Failed to refetch track for stale team', teamId, err);
        }
      })();
    }
  }, [selectedGameId]);

  // Subscribe to real-time location updates
  const handleLocationUpdate = useCallback((message: { payload: unknown }) => {
    const payload = message.payload;

    // The admin map always receives batched arrays (see LocationBroadcastBuffer), but
    // handle a lone object defensively too. Both paths update markers and grow tracks.
    const incoming = (Array.isArray(payload) ? payload : [payload]) as TeamLocation[];
    if (incoming.length === 0) return;

    // Tracked teams with a gap larger than the stale threshold likely have backfilled
    // points we never saw live, so extend the rest and refetch those whole tracks.
    const prevById = new Map(teamLocationsRef.current.map(l => [l.teamId, l]));
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(
      incoming, prevById, trackTeamIdsRef.current, STALE_AFTER_MS,
    );

    setTeamLocations(prev => mergeLocations(prev, incoming));
    setTeamTracks(prev => appendTrackPoints(prev, freshUpdates, trackTeamIdsRef.current));
    refetchTracks(staleTracked);
  }, [refetchTracks]);

  useEffect(() => {
    if (!selectedGameId) return;
    return subscribe('LOCATION_UPDATE', handleLocationUpdate);
  }, [selectedGameId, subscribe, handleLocationUpdate]);

  // Build map markers (apply legend visibility filters)
  const markers: MapMarker[] = [
    ...teamLocations
      .filter(loc => !hiddenTeamIds.has(loc.teamId))
      .map(loc => {
        const ts = Date.parse(loc.locationTime);
        return {
          id: loc.teamId,
          lat: loc.latitude,
          lng: loc.longitude,
          label: loc.teamName,
          color: TEAM_COLOR,
          type: 'team' as const,
          locationTime: loc.locationTime,
          stale: !Number.isNaN(ts) && now - ts > STALE_AFTER_MS,
        };
      }),
    ...checkpoints
      .filter(cp => cp.latitude != null && cp.longitude != null)
      .filter(cp => !hiddenCheckpointIds.has(cp.id))
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
        openingTime: cp.openingTime,
        closingTime: cp.closingTime,
        hidden: cp.hidden,
      })),
  ];

  const tracks: MapTrack[] = useMemo(
    () => Object.entries(teamTracks).map(([teamId, points]) => ({
      teamId,
      color: TEAM_COLOR,
      points,
    })),
    [teamTracks],
  );

  const legendTeams = useMemo(
    () => teamLocations.map(loc => ({
      id: loc.teamId,
      name: loc.teamName,
      description: loc.teamDescription,
      color: TEAM_COLOR,
      locationTime: loc.locationTime,
    })),
    [teamLocations],
  );

  const legendCheckpoints = useMemo(
    () => checkpoints.map(cp => ({
      id: cp.id,
      name: cp.name,
      hasLocation: cp.latitude != null && cp.longitude != null,
    })),
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
        next[teamId] = toTrackPoints(points);
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

  const handleZoomTeam = useCallback((id: string) => {
    const loc = teamLocations.find(l => l.teamId === id);
    if (loc) mapRef.current?.flyToPoint(loc.longitude, loc.latitude);
  }, [teamLocations]);

  const handleZoomCheckpoint = useCallback((id: string) => {
    const cp = checkpoints.find(c => c.id === id);
    if (cp?.latitude != null && cp?.longitude != null) {
      mapRef.current?.flyToPoint(cp.longitude, cp.latitude);
    }
  }, [checkpoints]);

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
              ref={mapRef}
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
            now={now}
            onToggleTeam={handleToggleTeam}
            onToggleCheckpoint={handleToggleCheckpoint}
            onToggleTrack={handleToggleTrack}
            onSetTeamsHidden={handleSetTeamsHidden}
            onSetCheckpointsHidden={handleSetCheckpointsHidden}
            onWindowChange={setWindowMinutes}
            onZoomTeam={handleZoomTeam}
            onZoomCheckpoint={handleZoomCheckpoint}
          />
        </Box>
      )}
    </Box>
  );
}

