/**
 * Admin Pictures - moderate team-uploaded pictures (approve / reject) and upload admin photos.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import PictureImage from '../../components/PictureImage';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate';
import DownloadIcon from '@mui/icons-material/Download';
import { usePictureStore } from '../../store/usePictureStore';
import { useGameStore } from '../../store/useGameStore';
import type {
  Picture,
  PictureCursor,
  PictureListResponse,
  PictureStatus,
} from '../../types/entities';
import api from '../../services/api';
import { mockApi } from '../../services/mockApi';
import { websocket } from '../../services/websocket';
import { config } from '../../config';
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import {
  hasAnyRole,
  PICTURE_DOWNLOAD_ROLES,
  PICTURE_MODERATION_ROLES,
  PICTURE_UPLOAD_ROLES,
} from '../../services/auth';
import { storage } from '../../services/storage';

type TabKey = 'PENDING' | 'ALL' | 'APPROVED' | 'UPLOAD';

const MAX_BYTES = 10 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const STATUS_COLORS: Record<PictureStatus, 'default' | 'success' | 'warning' | 'error'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
};

/** The cursor for the next batch, or null when this was the last one. */
function cursorOf(page: PictureListResponse): PictureCursor | null {
  return page.nextCursorAt && page.nextCursorId
    ? { at: page.nextCursorAt, id: page.nextCursorId }
    : null;
}

export default function AdminPictures() {
  const { t } = useTranslation();
  const {
    moderationPictures,
    setModerationPictures,
    appendModerationPictures,
    upsertModerationPicture,
  } = usePictureStore();
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const token = storage.getAuthToken();
  const canModerate = !!token && hasAnyRole(token, [...PICTURE_MODERATION_ROLES, 'admin']);
  const canUpload   = !!token && hasAnyRole(token, PICTURE_UPLOAD_ROLES);
  const canDownload = !!token && hasAnyRole(token, PICTURE_DOWNLOAD_ROLES);

  const [tab, setTab] = useState<TabKey>(
    () => canModerate ? 'PENDING' : canUpload ? 'UPLOAD' : 'APPROVED'
  );
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [cursor, setCursor] = useState<PictureCursor | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<Picture | null>(null);
  const [rejectNotes, setRejectNotes] = useState('');
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  // Upload state
  const [uploading, setUploading] = useState(false);
  const [uploadPercent, setUploadPercent] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

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

  // One batch of the current tab. `next` is undefined for the first batch.
  const fetchPage = useCallback((next?: PictureCursor) => {
    const gameId = selectedGameId ?? undefined;
    return tab === 'PENDING'  ? apiToUse.getPendingPictures(gameId, next) :
           tab === 'APPROVED' ? apiToUse.getApprovedPictures(gameId, next) :
           apiToUse.getAllPictures(undefined, gameId, next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, selectedGameId]);

  // Reset to the first batch. Also the resync path: after a reconnect the whole list is
  // re-fetched from the top rather than trying to patch up the pages already scrolled through.
  const load = useCallback(() => {
    if (tab === 'UPLOAD') return;
    setLoading(true);
    setError(null);
    fetchPage()
      .then((res) => {
        if (res.data) {
          setModerationPictures(res.data.pictures);
          setCursor(cursorOf(res.data));
          setHasMore(res.data.hasMore);
        } else {
          setError(res.error || t('admin.pictures.loadFailed'));
        }
      })
      .catch(() => setError(t('admin.pictures.loadFailed')))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, fetchPage]);

  const loadMore = useCallback(() => {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    fetchPage(cursor)
      .then((res) => {
        if (res.data) {
          appendModerationPictures(res.data.pictures);
          setCursor(cursorOf(res.data));
          setHasMore(res.data.hasMore);
        } else {
          setError(res.error || t('admin.pictures.loadFailed'));
          setHasMore(false);
        }
      })
      .catch(() => {
        setError(t('admin.pictures.loadFailed'));
        setHasMore(false);
      })
      .finally(() => setLoadingMore(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cursor, loadingMore, fetchPage]);

  const sentinelRef = useInfiniteScroll({
    hasMore: hasMore && tab !== 'UPLOAD',
    loading: loading || loadingMore,
    onLoadMore: loadMore,
  });

  useEffect(() => {
    load();
  }, [load]);

  useAdminResync(load);

  // Only receive picture events from the backend while this page is mounted.
  useAdminTopicHeartbeat(ADMIN_TOPICS.PICTURES);

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

  const handleDownloadAll = async () => {
    if (!selectedGameId) return;
    setDownloading(true);
    setError(null);
    setNotice(null);
    try {
      const res = await apiToUse.requestPictureDownloadToken(selectedGameId);
      if (!res.data) {
        setError(res.error || t('admin.pictures.downloadFailed'));
        return;
      }
      if (config.useMocks) {
        setNotice(t('admin.pictures.downloadMockNotice'));
        return;
      }
      // Navigating to an attachment response hands the transfer to the browser without
      // unloading the SPA, so the archive streams to disk instead of into page memory.
      window.location.href = apiToUse.pictureArchiveUrl(res.data.token);
      setNotice(t('admin.pictures.downloadPreparing'));
    } catch (e) {
      console.warn('Approved picture download failed', e);
      setError(t('admin.pictures.downloadFailed'));
    } finally {
      setDownloading(false);
    }
  };

  const handleAdminUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    if (!ALLOWED_TYPES.includes(file.type)) {
      setError(t('admin.pictures.typeError'));
      return;
    }
    if (file.size > MAX_BYTES) {
      setError(t('admin.pictures.sizeError'));
      return;
    }

    setUploading(true);
    setError(null);
    setUploadPercent(0);
    try {
      const reservation = await apiToUse.requestAdminPictureUpload({
        contentType: file.type,
        fileName: file.name,
        sizeBytes: file.size,
      });
      if (!reservation.data) {
        setError(reservation.error || t('admin.pictures.uploadFailed'));
        return;
      }

      const { pictureId, uploadUrl, contentType } = reservation.data;
      const upload = await apiToUse.uploadFileToS3(uploadUrl, file, contentType, setUploadPercent);
      if (!upload.ok) {
        setError(t('admin.pictures.storageFailed'));
        return;
      }

      const confirmed = await apiToUse.confirmAdminPictureUpload(pictureId);
      if (confirmed.data) {
        upsertModerationPicture(confirmed.data as Picture);
      } else {
        setError(confirmed.error || t('admin.pictures.confirmFailed'));
      }
    } catch (e) {
      console.warn('Admin picture upload failed', e);
      setError(t('admin.pictures.uploadFailed'));
    } finally {
      setUploadPercent(null);
      setUploading(false);
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
        >
          <Typography variant="h4" component="h1">{t('admin.pictures.title')}</Typography>
          {canDownload && (
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={handleDownloadAll}
              disabled={!selectedGameId || downloading}
            >
              {t('admin.pictures.downloadAll')}
            </Button>
          )}
        </Stack>

        <Tabs value={tab} onChange={(_, value) => setTab(value)}>
          {canModerate && <Tab label={t('admin.pictures.tabPending')} value="PENDING" />}
          {canModerate && <Tab label={t('admin.pictures.tabAll')} value="ALL" />}
          <Tab label={t('admin.pictures.tabApproved')} value="APPROVED" />
          {canUpload && <Tab label={t('admin.pictures.tabUpload')} value="UPLOAD" />}
        </Tabs>

        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {notice && <Alert severity="info" onClose={() => setNotice(null)}>{notice}</Alert>}

        {tab === 'UPLOAD' ? (
          <Stack spacing={2}>
            <input
              ref={fileInputRef}
              type="file"
              accept={ALLOWED_TYPES.join(',')}
              style={{ display: 'none' }}
              onChange={handleAdminUpload}
            />
            <Box>
              <Button
                variant="contained"
                startIcon={<AddPhotoAlternateIcon />}
                onClick={() => { setError(null); fileInputRef.current?.click(); }}
                disabled={uploading}
              >
                {t('admin.pictures.addPhoto')}
              </Button>
            </Box>
            {uploadPercent !== null && (
              <Box>
                <Typography variant="caption" color="text.secondary">
                  {t('admin.pictures.uploading', { percent: uploadPercent })}
                </Typography>
                <LinearProgress variant="determinate" value={uploadPercent} />
              </Box>
            )}
          </Stack>
        ) : loading ? (
          <Box display="flex" justifyContent="center" sx={{ py: 4 }}>
            <CircularProgress />
          </Box>
        ) : visiblePictures.length === 0 && !hasMore ? (
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
                <PictureImage
                  src={picture.viewUrl}
                  alt={t('admin.pictures.photoAlt')}
                  onClick={() => setLightboxUrl(picture.viewUrl!)}
                />
                <Box sx={{ position: 'absolute', top: 8, left: 8 }}>
                  <Chip
                    size="small"
                    label={statusLabel(picture.status)}
                    color={STATUS_COLORS[picture.status]}
                  />
                </Box>
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
                {tab !== 'APPROVED' && (
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
                )}
              </Card>
            ))}
          </Box>
        )}

        {tab !== 'UPLOAD' && !loading && hasMore && (
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
            alt={t('admin.pictures.photoAlt')}
            style={{ maxWidth: '95vw', maxHeight: '95vh', objectFit: 'contain', display: 'block' }}
          />
        )}
      </Dialog>

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
