/**
 * Admin Pictures - moderate team-uploaded pictures (approve / reject).
 */
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardMedia,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import { usePictureStore } from '../../store/usePictureStore';
import type { Picture, PictureStatus } from '../../types/entities';
import api from '../../services/api';
import { mockApi } from '../../services/mockApi';
import { websocket } from '../../services/websocket';
import { config } from '../../config';
import { useAdminResync } from '../../hooks/useAdminSync';

type TabKey = 'PENDING' | 'ALL';

const STATUS_COLORS: Record<PictureStatus, 'default' | 'success' | 'warning' | 'error'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
};

export default function AdminPictures() {
  const { t } = useTranslation();
  const { moderationPictures, setModerationPictures, upsertModerationPicture } = usePictureStore();
  const [tab, setTab] = useState<TabKey>('PENDING');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<Picture | null>(null);
  const [rejectNotes, setRejectNotes] = useState('');

  const statusLabel = (status: PictureStatus): string => {
    const labels: Record<PictureStatus, string> = {
      PENDING: t('admin.pictures.statusPending'),
      APPROVED: t('admin.pictures.statusApproved'),
      REJECTED: t('admin.pictures.statusRejected'),
    };
    return labels[status];
  };

  // mockApi mirrors the picture methods of api; treat it as the same shape.
  const apiToUse = (config.useMocks ? mockApi : api) as unknown as typeof api;

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    const request = tab === 'PENDING' ? apiToUse.getPendingPictures() : apiToUse.getAllPictures();
    request
      .then((res) => {
        if (res.data) setModerationPictures(res.data as Picture[]);
        else setError(res.error || t('admin.pictures.loadFailed'));
      })
      .catch(() => setError(t('admin.pictures.loadFailed')))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  useEffect(() => {
    load();
  }, [load]);

  useAdminResync(load);

  useEffect(() => {
    if (config.useMocks) return;
    const onPending = (m: { payload: unknown }) => upsertModerationPicture(m.payload as Picture);
    const onModerated = (m: { payload: unknown }) => upsertModerationPicture(m.payload as Picture);
    const unsubPending = websocket.on('PICTURE_PENDING', onPending);
    const unsubModerated = websocket.on('PICTURE_MODERATED', onModerated);
    return () => {
      unsubPending();
      unsubModerated();
    };
  }, [upsertModerationPicture]);

  const visiblePictures =
    tab === 'PENDING'
      ? moderationPictures.filter((p) => p.status === 'PENDING' && p.uploadedAt)
      : moderationPictures;

  const handleApprove = async (picture: Picture) => {
    const res = await apiToUse.approvePicture(picture.id);
    if (res.data) {
      upsertModerationPicture(res.data as Picture);
    } else {
      setError(res.error || t('admin.pictures.approveFailed'));
    }
  };

  const handleReject = async () => {
    if (!rejectTarget) return;
    const target = rejectTarget;
    const notes = rejectNotes.trim();
    setRejectTarget(null);
    setRejectNotes('');
    const res = await apiToUse.rejectPicture(target.id, notes || undefined);
    if (res.data) {
      upsertModerationPicture(res.data as Picture);
    } else {
      setError(res.error || t('admin.pictures.rejectFailed'));
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">{t('admin.pictures.title')}</Typography>

        <Tabs value={tab} onChange={(_, value) => setTab(value)}>
          <Tab label={t('admin.pictures.tabPending')} value="PENDING" />
          <Tab label={t('admin.pictures.tabAll')} value="ALL" />
        </Tabs>

        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        {loading ? (
          <Box display="flex" justifyContent="center" sx={{ py: 4 }}>
            <CircularProgress />
          </Box>
        ) : visiblePictures.length === 0 ? (
          <Typography color="text.secondary">{t('admin.pictures.noPhotos')}</Typography>
        ) : (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', md: 'repeat(4, 1fr)' },
              gap: 2,
            }}
          >
            {visiblePictures.map((picture) => (
              <Card key={picture.id} sx={{ position: 'relative' }}>
                {picture.viewUrl && (
                  <CardMedia
                    component="img"
                    image={picture.viewUrl}
                    alt={t('admin.pictures.photoAlt')}
                    sx={{ aspectRatio: '3 / 2', objectFit: 'cover' }}
                  />
                )}
                <Box sx={{ position: 'absolute', top: 8, left: 8 }}>
                  <Chip
                    size="small"
                    label={statusLabel(picture.status)}
                    color={STATUS_COLORS[picture.status]}
                  />
                </Box>
                <CardActions sx={{ justifyContent: 'space-between' }}>
                  <Button
                    size="small"
                    color="success"
                    startIcon={<CheckCircleIcon />}
                    onClick={() => handleApprove(picture)}
                    disabled={picture.status === 'APPROVED'}
                  >
                    {t('admin.pictures.approve')}
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    startIcon={<CancelIcon />}
                    onClick={() => setRejectTarget(picture)}
                    disabled={picture.status === 'REJECTED'}
                  >
                    {t('admin.pictures.reject')}
                  </Button>
                </CardActions>
              </Card>
            ))}
          </Box>
        )}
      </Stack>

      <Dialog open={!!rejectTarget} onClose={() => setRejectTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>{t('admin.pictures.rejectDialogTitle')}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            margin="dense"
            label={t('admin.pictures.rejectReason')}
            value={rejectNotes}
            onChange={(e) => setRejectNotes(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)}>{t('common.cancel')}</Button>
          <Button color="error" variant="contained" onClick={handleReject}>
            {t('admin.pictures.reject')}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
