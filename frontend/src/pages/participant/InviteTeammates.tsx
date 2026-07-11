/**
 * Team-facing invite page: shows a QR that teammates scan to join this team directly.
 *
 * The QR encodes a `/check-in?invite=<token>` URL. A fresh, generously-scoped invite
 * token is minted every time this page mounts, so there is nothing to refresh by hand.
 * Scanning the QR skips the game/team pickers (jumps to the nickname step) and bypasses
 * the digital check-in time gate.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { ContentCopy as CopyIcon, QrCode2 as QrIcon } from '@mui/icons-material';
import QRCode from 'qrcode';
import api from '../../services/api';
import { useTeamStore } from '../../store/useTeamStore';

export default function InviteTeammatesPage() {
  const { t } = useTranslation();
  const { team } = useTeamStore();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [link, setLink] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setCopied(false);
    try {
      const response = await api.getTeamInviteToken();
      if (response.error || !response.data) {
        setError(t('participant.invite.error'));
        return;
      }
      setLink(response.data.url);
    } catch (err) {
      console.error('invite token error:', err);
      setError(t('participant.invite.error'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  // Render the invite URL into the canvas once we have both a link and the element.
  useEffect(() => {
    if (!link || !canvasRef.current) return;
    QRCode.toCanvas(canvasRef.current, link, {
      width: 288,
      margin: 2,
      errorCorrectionLevel: 'Q',
    }).catch(() => setError(t('participant.invite.error')));
  }, [link, t]);

  const handleCopy = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
    } catch {
      setError(t('participant.invite.copyFailed'));
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('participant.invite.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('participant.invite.subtitle', { team: team?.name ?? '' })}
        </Typography>

        <Paper sx={{ p: 3 }}>
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress />
            </Box>
          ) : error ? (
            <Stack spacing={2} alignItems="center">
              <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>
              <Button variant="outlined" onClick={load}>{t('common.retry')}</Button>
            </Stack>
          ) : (
            <Stack spacing={2} alignItems="center">
              <QrIcon color="primary" />
              <Typography variant="body2" align="center" color="text.secondary">
                {t('participant.invite.instructions')}
              </Typography>
              <Box
                sx={{ bgcolor: '#fff', p: 2, borderRadius: 1, lineHeight: 0 }}
              >
                <canvas ref={canvasRef} />
              </Box>
              {copied && (
                <Alert severity="success" sx={{ width: '100%' }}>
                  {t('participant.invite.linkCopied')}
                </Alert>
              )}
              <Button startIcon={<CopyIcon />} onClick={handleCopy}>
                {t('participant.invite.copyLink')}
              </Button>
            </Stack>
          )}
        </Paper>
      </Stack>
    </Container>
  );
}
