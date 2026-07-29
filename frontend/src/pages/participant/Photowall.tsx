/**
 * Photowall Page - a read-only wall of every team's approved pictures for the
 * player's game, newest first. Tapping a tile opens a full-screen lightbox.
 *
 * Uploads live on the separate Pictures page; this screen only shows approved shots.
 *
 * Live updates: the API buffers newly-approved photos and pushes them as a batched
 * `PICTURE_APPROVED` array (mirrors the location buffer). Rather than dropping a whole
 * batch in at once, we queue the new photos and reveal them one at a time on a short
 * timer, so they appear to stream in. A refetch on tab focus covers anything missed
 * while disconnected, plus a slow safety poll while the page stays open.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Card,
  CardContent,
  CircularProgress,
  Container,
  Dialog,
  Fade,
  Stack,
  Typography,
} from '@mui/material';
import PictureImage from '../../components/PictureImage';
import { useTeamStore } from '../../store/useTeamStore';
import { useWebSocketSubscription } from '../../hooks/useWebSocket';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import type { PictureCursor, PublicPicture } from '../../types/entities';
import api from '../../services/api';
import { mockApi } from '../../services/mockApi';
import { config } from '../../config';

// How fast queued live photos are revealed, one at a time.
const REVEAL_MS = 600;
// Slow safety refetch while the page is open, in case a live event was missed.
const SAFETY_POLL_MS = 60_000;

export default function Photowall() {
  const { t } = useTranslation();
  const { team } = useTeamStore();
  const { subscribe } = useWebSocketSubscription();

  const [pictures, setPictures] = useState<PublicPicture[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [cursor, setCursor] = useState<PictureCursor | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  // Photos waiting to be revealed one-by-one, and every id we've already shown or queued
  // (so a batch that overlaps the current wall never reveals or renders a duplicate).
  const queueRef = useRef<PublicPicture[]>([]);
  const seenRef = useRef<Set<string>>(new Set());

  // mockApi mirrors the picture methods of api; treat it as the same shape.
  const apiToUse = (config.useMocks ? mockApi : api) as unknown as typeof api;

  // Initial load + focus/visibility refetch + a slow safety poll.
  useEffect(() => {
    if (!team) return;
    let cancelled = false;

    // A background refetch only pulls the *first* batch and merges what it hasn't seen to the
    // top — replacing the list would throw away every batch the player has scrolled to.
    const load = async (background: boolean) => {
      if (!background) setLoading(true);
      try {
        const res = await apiToUse.getPhotowall(team.id);
        if (cancelled) return;
        if (res.data) {
          const { pictures: batch, nextCursorAt, nextCursorId, hasMore: more } = res.data;
          const fresh = batch.filter((p) => !seenRef.current.has(p.id));
          batch.forEach((p) => seenRef.current.add(p.id));
          if (background) {
            if (fresh.length > 0) setPictures((prev) => [...fresh, ...prev]);
          } else {
            setPictures(batch);
            setCursor(nextCursorAt && nextCursorId ? { at: nextCursorAt, id: nextCursorId } : null);
            setHasMore(more);
          }
          setError(null);
        } else if (!background) {
          setError(t('participant.photowall.loadError'));
        }
      } catch {
        if (!cancelled && !background) setError(t('participant.photowall.loadError'));
      } finally {
        if (!cancelled && !background) setLoading(false);
      }
    };

    load(false);

    const interval = setInterval(() => {
      if (document.visibilityState === 'visible') load(true);
    }, SAFETY_POLL_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible') load(true);
    };
    window.addEventListener('focus', onVisible);
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      cancelled = true;
      clearInterval(interval);
      window.removeEventListener('focus', onVisible);
      document.removeEventListener('visibilitychange', onVisible);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [team]);

  // Reveal queued photos one at a time so a batch looks like it streams in.
  useEffect(() => {
    const id = setInterval(() => {
      const next = queueRef.current.shift();
      if (!next) return;
      setPictures((prev) => (prev.some((p) => p.id === next.id) ? prev : [next, ...prev]));
    }, REVEAL_MS);
    return () => clearInterval(id);
  }, []);

  // Older photos, fetched a batch at a time as the player scrolls back through the wall.
  const loadMore = useCallback(async () => {
    if (!team || !cursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const res = await apiToUse.getPhotowall(team.id, cursor);
      if (res.data) {
        const { pictures: batch, nextCursorAt, nextCursorId, hasMore: more } = res.data;
        const fresh = batch.filter((p) => !seenRef.current.has(p.id));
        fresh.forEach((p) => seenRef.current.add(p.id));
        setPictures((prev) => [...prev, ...fresh]);
        setCursor(nextCursorAt && nextCursorId ? { at: nextCursorAt, id: nextCursorId } : null);
        setHasMore(more);
      } else {
        setHasMore(false);
      }
    } catch {
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [team, cursor, loadingMore]);

  const sentinelRef = useInfiniteScroll({
    hasMore,
    loading: loading || loadingMore,
    onLoadMore: () => void loadMore(),
  });

  // Batched live approvals: enqueue the ones we haven't seen, oldest first so each
  // newer photo pops in on top.
  const handleApproved = useCallback((message: { payload: unknown }) => {
    const payload = message.payload;
    const incoming = (Array.isArray(payload) ? payload : [payload]) as PublicPicture[];
    const fresh = incoming.filter((p) => p && p.id && p.viewUrl && !seenRef.current.has(p.id));
    if (fresh.length === 0) return;
    fresh.forEach((p) => seenRef.current.add(p.id));
    fresh.sort((a, b) => (a.uploadedAt ?? '').localeCompare(b.uploadedAt ?? ''));
    queueRef.current.push(...fresh);
  }, []);

  useEffect(() => subscribe('PICTURE_APPROVED', handleApproved), [subscribe, handleApproved]);

  if (!team) {
    return (
      <Container maxWidth="md" sx={{ mt: 3 }}>
        <Typography>{t('participant.photowall.loadingTeam')}</Typography>
      </Container>
    );
  }

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('participant.photowall.title')}
        </Typography>

        {loading ? (
          <Box display="flex" justifyContent="center" sx={{ py: 4 }}>
            <CircularProgress />
          </Box>
        ) : error ? (
          <Typography color="error">{error}</Typography>
        ) : pictures.length === 0 ? (
          <Typography color="text.secondary">{t('participant.photowall.empty')}</Typography>
        ) : (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', md: 'repeat(4, 1fr)' },
              gap: 2,
            }}
          >
            {pictures.map((picture) => (
              <Fade key={picture.id} in timeout={500}>
                <Card>
                  <PictureImage
                    src={picture.viewUrl}
                    alt={t('participant.photowall.photoAlt')}
                    onClick={() => setLightboxUrl(picture.viewUrl!)}
                  />
                  {picture.teamName && (
                    <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 } }}>
                      <Typography variant="subtitle2" noWrap title={picture.teamName}>
                        {picture.teamName}
                      </Typography>
                      {picture.teamDescription && (
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          noWrap
                          display="block"
                          title={picture.teamDescription}
                        >
                          {picture.teamDescription}
                        </Typography>
                      )}
                    </CardContent>
                  )}
                </Card>
              </Fade>
            ))}
          </Box>
        )}

        {!loading && !error && hasMore && (
          <Box ref={sentinelRef} display="flex" justifyContent="center" sx={{ py: 3 }}>
            {loadingMore && <CircularProgress size={28} />}
          </Box>
        )}
      </Stack>

      <Dialog
        open={!!lightboxUrl}
        onClose={() => setLightboxUrl(null)}
        maxWidth={false}
        slotProps={{ paper: { sx: { bgcolor: 'black', m: 1 } } }}
      >
        {lightboxUrl && (
          <img
            src={lightboxUrl}
            alt={t('participant.photowall.photoAlt')}
            style={{ maxWidth: '95vw', maxHeight: '95vh', objectFit: 'contain', display: 'block' }}
          />
        )}
      </Dialog>
    </Container>
  );
}
