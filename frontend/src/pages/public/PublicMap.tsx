/**
 * Public Map Page - login-less live map for narrowcasting displays.
 *
 * Opened by narrowcasting software as `/public/map?g=<gameId>&t=<token>` and then left running for
 * hours unattended. That shapes every decision here:
 *
 * - No layout chrome, no navigation, no auth guard. The token in the URL is the whole credential.
 * - Polling rather than websockets: a display does not need sub-second updates, and a dropped poll
 *   heals itself on the next tick with no reconnect logic.
 * - A failed poll keeps the last good frame on screen. A display going blank because the API
 *   hiccuped is worse than a map that is thirty seconds stale, so only a permanently broken link
 *   (bad token, unknown game) replaces the map with a message.
 * - Hidden zones are never rendered: the server does not send them, and the payload has no `hidden`
 *   field to render even if it did.
 */
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Typography } from '@mui/material';
import type { PublicMapResponse } from '../../types/entities';
import { fetchPublicMap, PublicAccessDeniedError } from '../../services/publicApi';
import { toMarkers } from './publicMapMarkers';
import { DEFAULT_CENTER, DEFAULT_ZOOM } from '../../utils/mapStyle';

const MapView = lazy(() => import('../../components/MapView'));

/** How often the display refetches. Slow enough to be gentle, fast enough to look live. */
const POLL_INTERVAL_MS = 15_000;

/** Re-evaluate marker staleness on this cadence, so dots dim without waiting for a poll. */
const STALENESS_TICK_MS = 30_000;

export default function PublicMap() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const gameId = searchParams.get('g');
  const token = searchParams.get('t');

  const [data, setData] = useState<PublicMapResponse | null>(null);
  const [loading, setLoading] = useState(true);
  // Only set for failures a retry cannot fix — a bad token or a game that does not exist.
  const [fatalError, setFatalError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  // Kept in a ref so the polling effect does not restart (and re-fire a request) on every tick.
  const dataRef = useRef<PublicMapResponse | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    if (!gameId || !token) return;
    try {
      const payload = await fetchPublicMap(gameId, token, signal);
      if (signal?.aborted) return;
      dataRef.current = payload;
      setData(payload);
      setNow(Date.now());
      setFatalError(null);
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === 'AbortError')) return;
      if (err instanceof PublicAccessDeniedError) {
        setFatalError(t('public.map.invalidLink'));
      } else {
        // Transient: keep whatever is on screen and try again on the next tick.
        console.warn('[PublicMap] poll failed, keeping last known state:', err);
      }
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [gameId, token, t]);

  // Poll, pausing while the tab is hidden and refreshing immediately when it comes back — a display
  // that was backgrounded should not paint a stale frame before its next scheduled tick.
  useEffect(() => {
    if (!gameId || !token) {
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    load(controller.signal);

    const id = setInterval(() => {
      if (document.visibilityState === 'visible') {
        load(controller.signal);
      }
    }, POLL_INTERVAL_MS);

    const onVisible = () => {
      if (document.visibilityState === 'visible') load(controller.signal);
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      controller.abort();
      clearInterval(id);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [gameId, token, load]);

  // Re-tick staleness between polls so a team that stops reporting fades out on its own.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), STALENESS_TICK_MS);
    return () => clearInterval(id);
  }, []);

  const markers = useMemo(() => (data ? toMarkers(data, now) : []), [data, now]);

  const message = !gameId || !token ? t('public.map.missingLink') : fatalError;

  if (message) {
    return <FullScreenMessage text={message} />;
  }

  if (loading && !data) {
    return (
      <Box sx={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100vh', width: '100vw' }}>
      <Suspense fallback={
        <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <CircularProgress />
        </Box>
      }>
        {/* No controls and auto-refit: nobody is standing at a narrowcasting screen to press a
            button or pan it back after the display changes size. */}
        <MapView
          markers={markers}
          center={DEFAULT_CENTER}
          zoom={DEFAULT_ZOOM}
          showControls={false}
          refitOnResize
        />
      </Suspense>
    </Box>
  );
}

function FullScreenMessage({ text }: { text: string }) {
  return (
    <Box
      sx={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 4,
        textAlign: 'center',
      }}
    >
      <Typography color="text.secondary">{text}</Typography>
    </Box>
  );
}
