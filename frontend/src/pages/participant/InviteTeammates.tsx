/**
 * Team-facing invite page: shows a QR that teammates scan to join this team directly.
 *
 * The QR encodes a short `/I/<CODE>` URL — short and uppercase so the QR stays coarse
 * enough for a phone to read off another phone's screen. Scanning it skips the game/team
 * pickers (jumps to the nickname step) and bypasses the digital check-in time gate.
 *
 * The code is short-lived, so this page re-mints it before it expires (and whenever the tab
 * comes back to the foreground, since phones suspend timers while backgrounded). The backend
 * hands back the team's existing code with a slid TTL, so the QR on screen does not change.
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
  const [qrUrl, setQrUrl] = useState<string | null>(null);
  const [code, setCode] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  // Wrapped in an object so that re-minting reschedules the refresh even when the TTL
  // comes back unchanged (which is the normal case — the backend slides the same code).
  const [expiry, setExpiry] = useState<{ seconds: number } | null>(null);

  // Store a minted invite in state. `silent` is for the background re-mints:
  // they must not surface errors or flash UI out from under the user.
  const applyInvite = useCallback((response: Awaited<ReturnType<typeof api.getTeamInviteToken>>, silent: boolean) => {
    if (response.error || !response.data) {
      if (!silent) setError(t('participant.invite.error'));
      return;
    }
    setLink(response.data.url);
    setQrUrl(response.data.qrUrl);
    setCode(response.data.token);
    setExpiry({ seconds: response.data.expiresInSeconds });
  }, [t]);

  // Re-mint from timers / visibility changes / the retry button; the mount
  // effect below owns the first load.
  const load = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
      setError(null);
      setCopied(false);
    }
    try {
      const response = await api.getTeamInviteToken();
      applyInvite(response, silent);
    } catch (err) {
      console.error('invite token error:', err);
      if (!silent) setError(t('participant.invite.error'));
    } finally {
      if (!silent) setLoading(false);
    }
  }, [applyInvite, t]);

  // First mint. loading starts true and error/copied start clear, so the
  // non-silent presets in load() would be no-ops here anyway.
  useEffect(() => {
    let cancelled = false;
    api
      .getTeamInviteToken()
      .then((response) => {
        if (!cancelled) applyInvite(response, false);
      })
      .catch((err: unknown) => {
        console.error('invite token error:', err);
        if (!cancelled) setError(t('participant.invite.error'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [applyInvite, t]);

  // The code expires, so re-mint a minute before it does. A team can leave this page open
  // on a table for the whole rally; the QR has to still work when someone finally scans it.
  useEffect(() => {
    if (!expiry) return;
    const delayMs = Math.max(30, expiry.seconds - 60) * 1000;
    const timer = window.setTimeout(() => { void load(true); }, delayMs);
    return () => window.clearTimeout(timer);
  }, [expiry, load]);

  // Phones suspend timers for backgrounded tabs, so the timeout above cannot be relied on
  // alone — re-mint whenever the page comes back to the foreground.
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') void load(true);
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [load]);

  // Render the QR-optimised (uppercase, short) URL into the canvas — not `link`.
  useEffect(() => {
    if (!qrUrl || !canvasRef.current) return;
    QRCode.toCanvas(canvasRef.current, qrUrl, {
      width: 288,
      margin: 2,
      errorCorrectionLevel: 'Q',
    }).catch(() => setError(t('participant.invite.error')));
  }, [qrUrl, t]);

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
              <Button variant="outlined" onClick={() => void load()}>{t('common.retry')}</Button>
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
              {/* A teammate whose camera won't cooperate can type this into the app instead — the
                  codes are minted from an alphabet with no I/L/O/U precisely so that works. */}
              {code && (
                <Stack spacing={0.5} alignItems="center">
                  <Typography variant="caption" color="text.secondary">
                    {t('participant.invite.codeLabel')}
                  </Typography>
                  <Typography
                    variant="h5"
                    component="p"
                    sx={{ fontFamily: 'monospace', letterSpacing: '0.15em', userSelect: 'all' }}
                  >
                    {code}
                  </Typography>
                </Stack>
              )}
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
