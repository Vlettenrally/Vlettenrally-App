/**
 * Pictures Page - lets a team upload up to 5 pictures.
 *
 * Flow per file: request a pre-signed upload URL from the backend, PUT the file
 * directly to storage, then confirm. Uploaded pictures are moderated by an admin
 * before they appear in the public slideshow.
 */
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Button,
  Card,
  CardMedia,
  Chip,
  CircularProgress,
  Container,
  IconButton,
  LinearProgress,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate';
import DeleteIcon from '@mui/icons-material/Delete';
import PublicIcon from '@mui/icons-material/Public';
import { useTeamStore } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import { usePictureStore } from '../../store/usePictureStore';
import type { Picture, PictureStatus } from '../../types/entities';
import api from '../../services/api';
import { mockApi } from '../../services/mockApi';
import { config } from '../../config';

const MAX_PICTURES = 5;
const MAX_BYTES = 10 * 1024 * 1024; // 10 MB
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export default function Pictures() {
  const { t } = useTranslation();
  const { team } = useTeamStore();
  const { game } = useGameStore();
  const {
    pictures, setPictures, upsertPicture, removePicture,
    uploadProgress, setUploadProgress, clearUploadProgress,
  } = usePictureStore();

  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // mockApi mirrors the picture methods of api; treat it as the same shape.
  const apiToUse = (config.useMocks ? mockApi : api) as unknown as typeof api;

  useEffect(() => {
    if (!team) return;
    setLoading(true);
    apiToUse.getMyPictures(team.id)
      .then((res) => {
        if (res.data) setPictures(res.data as Picture[]);
      })
      .catch(() => setError(t('participant.pictures.loadError')))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [team]);

  // A team can only have MAX_PICTURES that are not rejected at any time.
  const activeCount = pictures.filter((p) => p.status !== 'REJECTED').length;
  const slotsFull = activeCount >= MAX_PICTURES;
  // Uploading is only allowed while the game is ongoing (the backend enforces this too).
  // In mock mode there is no real game status loaded, so don't block the demo.
  const gameOngoing = config.useMocks || game?.status === 'active';

  const handlePick = () => {
    setError(null);
    fileInputRef.current?.click();
  };

  const handleFileSelected = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Reset the input so picking the same file again re-triggers onChange.
    event.target.value = '';
    if (!file || !team) return;

    if (!ALLOWED_TYPES.includes(file.type)) {
      setError(t('participant.pictures.typeError'));
      return;
    }
    if (file.size > MAX_BYTES) {
      setError(t('participant.pictures.sizeError'));
      return;
    }

    const progressKey = `${file.name}-${Date.now()}`;
    setUploading(true);
    setError(null);
    setUploadProgress(progressKey, 0);
    try {
      const reservation = await apiToUse.requestPictureUpload(team.id, {
        contentType: file.type,
        fileName: file.name,
        sizeBytes: file.size,
      });
      if (!reservation.data) {
        setError(reservation.error || t('participant.pictures.uploadFailed'));
        return;
      }

      const { pictureId, uploadUrl, contentType } = reservation.data;
      const upload = await apiToUse.uploadFileToS3(uploadUrl, file, contentType, (p) =>
        setUploadProgress(progressKey, p),
      );
      if (!upload.ok) {
        setError(t('participant.pictures.storageFailed'));
        return;
      }

      const confirmed = await apiToUse.confirmPictureUpload(team.id, pictureId);
      if (confirmed.data) {
        upsertPicture(confirmed.data as Picture);
      } else {
        setError(confirmed.error || t('participant.pictures.confirmFailed'));
      }
    } catch (e) {
      console.warn('Picture upload failed', e);
      setError(t('participant.pictures.uploadRetry'));
    } finally {
      clearUploadProgress(progressKey);
      setUploading(false);
    }
  };

  const handleDelete = async (picture: Picture) => {
    if (!team) return;
    const res = await apiToUse.deletePicture(team.id, picture.id);
    if (res.status === 204 || res.status === 200) {
      removePicture(picture.id);
    } else {
      setError(res.error || t('participant.pictures.deleteFailed'));
    }
  };

  if (!team) {
    return (
      <Container maxWidth="md" sx={{ mt: 3 }}>
        <Typography>{t('participant.pictures.loadingTeam')}</Typography>
      </Container>
    );
  }

  const activeProgress = Object.values(uploadProgress);
  const statusLabels: Record<PictureStatus, { label: string; color: 'default' | 'success' | 'warning' | 'error' }> = {
    PENDING: { label: t('participant.pictures.pending'), color: 'warning' },
    APPROVED: { label: t('participant.pictures.approved'), color: 'success' },
    REJECTED: { label: t('participant.pictures.rejected'), color: 'error' },
  };

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Typography variant="h4" component="h1">
            {t('participant.pictures.title')}
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddPhotoAlternateIcon />}
            onClick={handlePick}
            disabled={uploading || slotsFull || !gameOngoing}
          >
            {t('participant.pictures.addPhoto')}
          </Button>
        </Box>

        <Typography variant="body2" color="text.secondary">
          {t('participant.pictures.uploadLimit', { max: MAX_PICTURES, current: activeCount })}
        </Typography>

        {!gameOngoing && (
          <Alert severity="warning">
            {t('participant.pictures.rallyNotActive')}
          </Alert>
        )}

        <Alert severity="info" icon={<PublicIcon />}>
          {t('participant.pictures.publicWarning')}
        </Alert>

        <input
          ref={fileInputRef}
          type="file"
          accept={ALLOWED_TYPES.join(',')}
          style={{ display: 'none' }}
          onChange={handleFileSelected}
        />

        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        {activeProgress.map((percent, i) => (
          <Box key={i}>
            <Typography variant="caption" color="text.secondary">{t('participant.pictures.uploading', { percent })}</Typography>
            <LinearProgress variant="determinate" value={percent} />
          </Box>
        ))}

        {loading ? (
          <Box display="flex" justifyContent="center" sx={{ py: 4 }}>
            <CircularProgress />
          </Box>
        ) : pictures.length === 0 ? (
          <Typography color="text.secondary">{t('participant.pictures.noPhotos')}</Typography>
        ) : (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' },
              gap: 2,
            }}
          >
            {pictures.map((picture) => {
              const status = statusLabels[picture.status];
              return (
                <Card key={picture.id} sx={{ position: 'relative' }}>
                  {picture.viewUrl ? (
                    <CardMedia
                      component="img"
                      image={picture.viewUrl}
                      alt={t('participant.pictures.photoAlt')}
                      sx={{ aspectRatio: '3 / 2', objectFit: 'cover' }}
                    />
                  ) : (
                    <Box sx={{ aspectRatio: '3 / 2', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'action.hover' }}>
                      <CircularProgress size={24} />
                    </Box>
                  )}
                  <Box sx={{ position: 'absolute', top: 8, left: 8 }}>
                    <Chip size="small" label={status.label} color={status.color} />
                  </Box>
                  {picture.status === 'PENDING' && (
                    <Box sx={{ position: 'absolute', top: 4, right: 4 }}>
                      <Tooltip title={t('participant.pictures.delete')}>
                        <IconButton
                          size="small"
                          onClick={() => handleDelete(picture)}
                          sx={{ bgcolor: 'rgba(255,255,255,0.8)', '&:hover': { bgcolor: 'white' } }}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  )}
                </Card>
              );
            })}
          </Box>
        )}
      </Stack>
    </Container>
  );
}
