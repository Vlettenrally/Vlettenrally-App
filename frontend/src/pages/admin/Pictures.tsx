/**
 * Admin Pictures - moderate team-uploaded pictures (approve / reject).
 */
import { useCallback, useEffect, useState } from 'react';
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

type TabKey = 'PENDING' | 'ALL';

const STATUS_LABELS: Record<PictureStatus, { label: string; color: 'default' | 'success' | 'warning' | 'error' }> = {
  PENDING: { label: 'In behandeling', color: 'warning' },
  APPROVED: { label: 'Goedgekeurd', color: 'success' },
  REJECTED: { label: 'Afgewezen', color: 'error' },
};

export default function AdminPictures() {
  const { moderationPictures, setModerationPictures, upsertModerationPicture } = usePictureStore();
  const [tab, setTab] = useState<TabKey>('PENDING');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<Picture | null>(null);
  const [rejectNotes, setRejectNotes] = useState('');

  // mockApi mirrors the picture methods of api; treat it as the same shape.
  const apiToUse = (config.useMocks ? mockApi : api) as unknown as typeof api;

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    const request = tab === 'PENDING' ? apiToUse.getPendingPictures() : apiToUse.getAllPictures();
    request
      .then((res) => {
        if (res.data) setModerationPictures(res.data as Picture[]);
        else setError(res.error || 'Kon foto’s niet laden.');
      })
      .catch(() => setError('Kon foto’s niet laden.'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  useEffect(() => {
    load();
  }, [load]);

  // Live updates over the admin WebSocket (the connection is owned by AdminLayout;
  // we only attach handlers to the shared singleton, so we never disconnect it).
  // Skipped in mock mode, which has no backend socket.
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

  // Render-time filter: a picture moderated elsewhere (locally or via WebSocket) flips
  // status and drops out of the "Te beoordelen" view without a refetch.
  const visiblePictures =
    tab === 'PENDING'
      ? moderationPictures.filter((p) => p.status === 'PENDING' && p.uploadedAt)
      : moderationPictures;

  const handleApprove = async (picture: Picture) => {
    const res = await apiToUse.approvePicture(picture.id);
    if (res.data) {
      upsertModerationPicture(res.data as Picture);
    } else {
      setError(res.error || 'Goedkeuren mislukt.');
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
      setError(res.error || 'Afwijzen mislukt.');
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">Foto-moderatie</Typography>

        <Tabs value={tab} onChange={(_, value) => setTab(value)}>
          <Tab label="Te beoordelen" value="PENDING" />
          <Tab label="Alle" value="ALL" />
        </Tabs>

        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        {loading ? (
          <Box display="flex" justifyContent="center" sx={{ py: 4 }}>
            <CircularProgress />
          </Box>
        ) : visiblePictures.length === 0 ? (
          <Typography color="text.secondary">Geen foto&apos;s om weer te geven.</Typography>
        ) : (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', md: 'repeat(4, 1fr)' },
              gap: 2,
            }}
          >
            {visiblePictures.map((picture) => {
              const status = STATUS_LABELS[picture.status];
              return (
                <Card key={picture.id} sx={{ position: 'relative' }}>
                  {picture.viewUrl && (
                    <CardMedia
                      component="img"
                      image={picture.viewUrl}
                      alt="Te modereren foto"
                      sx={{ aspectRatio: '3 / 2', objectFit: 'cover' }}
                    />
                  )}
                  <Box sx={{ position: 'absolute', top: 8, left: 8 }}>
                    <Chip size="small" label={status.label} color={status.color} />
                  </Box>
                  <CardActions sx={{ justifyContent: 'space-between' }}>
                    <Button
                      size="small"
                      color="success"
                      startIcon={<CheckCircleIcon />}
                      onClick={() => handleApprove(picture)}
                      disabled={picture.status === 'APPROVED'}
                    >
                      Goedkeuren
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      startIcon={<CancelIcon />}
                      onClick={() => setRejectTarget(picture)}
                      disabled={picture.status === 'REJECTED'}
                    >
                      Afwijzen
                    </Button>
                  </CardActions>
                </Card>
              );
            })}
          </Box>
        )}
      </Stack>

      <Dialog open={!!rejectTarget} onClose={() => setRejectTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Foto afwijzen</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            margin="dense"
            label="Reden (optioneel)"
            value={rejectNotes}
            onChange={(e) => setRejectNotes(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)}>Annuleren</Button>
          <Button color="error" variant="contained" onClick={handleReject}>Afwijzen</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
