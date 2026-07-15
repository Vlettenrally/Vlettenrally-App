/**
 * Photowall Page - a read-only wall of every team's approved pictures for the
 * player's game, newest first. Tapping a tile opens a full-screen lightbox.
 *
 * Uploads live on the separate Pictures page; this screen only shows approved shots.
 * The wall refreshes itself: a gentle interval while visible, plus an immediate
 * refetch whenever the tab regains focus, so newly approved photos appear without
 * a manual reload.
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Card,
  CircularProgress,
  Container,
  Dialog,
  Stack,
  Typography,
} from '@mui/material';
import PictureImage from '../../components/PictureImage';
import { useTeamStore } from '../../store/useTeamStore';
import type { PublicPicture } from '../../types/entities';
import api from '../../services/api';
import { mockApi } from '../../services/mockApi';
import { config } from '../../config';

// How often to refetch the wall while the page is visible.
const POLL_INTERVAL_MS = 30_000;

export default function Photowall() {
  const { t } = useTranslation();
  const { team } = useTeamStore();

  const [pictures, setPictures] = useState<PublicPicture[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  // mockApi mirrors the picture methods of api; treat it as the same shape.
  const apiToUse = (config.useMocks ? mockApi : api) as unknown as typeof api;

  useEffect(() => {
    if (!team) return;
    let cancelled = false;

    // Background refetches update quietly — no spinner, and a transient failure
    // leaves the already-shown wall in place rather than replacing it with an error.
    const load = async (background: boolean) => {
      if (!background) setLoading(true);
      try {
        const res = await apiToUse.getPhotowall(team.id);
        if (cancelled) return;
        if (res.data) {
          setPictures(res.data as PublicPicture[]);
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
    }, POLL_INTERVAL_MS);

    // Coming back to the tab/app should refresh immediately, not wait for the interval.
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
              <Card key={picture.id}>
                <PictureImage
                  src={picture.viewUrl}
                  alt={t('participant.photowall.photoAlt')}
                  onClick={() => setLightboxUrl(picture.viewUrl!)}
                />
              </Card>
            ))}
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
