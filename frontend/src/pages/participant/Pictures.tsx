/**
 * Pictures Page - lets a team upload up to 5 pictures.
 *
 * Flow per file: request a pre-signed upload URL from the backend, PUT the file
 * directly to storage, then confirm. Uploaded pictures are moderated by an admin
 * before they appear in the public slideshow.
 */
import { useEffect, useRef, useState } from 'react';
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

const STATUS_LABELS: Record<PictureStatus, { label: string; color: 'default' | 'success' | 'warning' | 'error' }> = {
  PENDING: { label: 'In behandeling', color: 'warning' },
  APPROVED: { label: 'Goedgekeurd', color: 'success' },
  REJECTED: { label: 'Afgewezen', color: 'error' },
};

export default function Pictures() {
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
      .catch(() => setError('Kon de foto’s niet laden.'))
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
      setError('Alleen JPEG-, PNG- of WebP-afbeeldingen zijn toegestaan.');
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('De afbeelding is te groot (max. 10 MB).');
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
        setError(reservation.error || 'Uploaden mislukt.');
        return;
      }

      const { pictureId, uploadUrl, contentType } = reservation.data;
      const upload = await apiToUse.uploadFileToS3(uploadUrl, file, contentType, (p) =>
        setUploadProgress(progressKey, p),
      );
      if (!upload.ok) {
        setError('Uploaden naar de opslag is mislukt.');
        return;
      }

      const confirmed = await apiToUse.confirmPictureUpload(team.id, pictureId);
      if (confirmed.data) {
        upsertPicture(confirmed.data as Picture);
      } else {
        setError(confirmed.error || 'Bevestigen van de upload is mislukt.');
      }
    } catch (e) {
      console.warn('Picture upload failed', e);
      setError('Uploaden mislukt. Probeer het opnieuw.');
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
      setError(res.error || 'Verwijderen mislukt.');
    }
  };

  if (!team) {
    return (
      <Container maxWidth="md" sx={{ mt: 3 }}>
        <Typography>Team laden…</Typography>
      </Container>
    );
  }

  const activeProgress = Object.values(uploadProgress);

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Typography variant="h4" component="h1">
            Foto&apos;s
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddPhotoAlternateIcon />}
            onClick={handlePick}
            disabled={uploading || slotsFull || !gameOngoing}
          >
            Foto toevoegen
          </Button>
        </Box>

        <Typography variant="body2" color="text.secondary">
          Je kunt maximaal {MAX_PICTURES} foto&apos;s uploaden ({activeCount}/{MAX_PICTURES} gebruikt).
        </Typography>

        {!gameOngoing && (
          <Alert severity="warning">
            Je kunt alleen foto&apos;s uploaden terwijl de rally bezig is.
          </Alert>
        )}

        <Alert severity="info" icon={<PublicIcon />}>
          Let op: foto&apos;s die je uploadt kunnen na goedkeuring <strong>openbaar zichtbaar</strong> zijn
          (bijvoorbeeld in de diavoorstelling). Upload geen foto&apos;s die niet iedereen mag zien.
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
            <Typography variant="caption" color="text.secondary">Uploaden… {percent}%</Typography>
            <LinearProgress variant="determinate" value={percent} />
          </Box>
        ))}

        {loading ? (
          <Box display="flex" justifyContent="center" sx={{ py: 4 }}>
            <CircularProgress />
          </Box>
        ) : pictures.length === 0 ? (
          <Typography color="text.secondary">Nog geen foto&apos;s geüpload.</Typography>
        ) : (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' },
              gap: 2,
            }}
          >
            {pictures.map((picture) => {
              const status = STATUS_LABELS[picture.status];
              return (
                <Card key={picture.id} sx={{ position: 'relative' }}>
                  {picture.viewUrl ? (
                    <CardMedia
                      component="img"
                      image={picture.viewUrl}
                      alt="Geüploade foto"
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
                      <Tooltip title="Verwijderen">
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
