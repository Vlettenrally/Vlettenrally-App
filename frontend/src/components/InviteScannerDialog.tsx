/**
 * Scans a teammate's invite QR from inside the app.
 *
 * This exists because the invite URL cannot reach an installed iOS PWA: the Camera app hands
 * `https://HOST/I/<CODE>` to Safari, which has a different storage container than the home-screen
 * app (and no web push). Scanning moves the code across that boundary — the QR sits on the other
 * phone's screen and can be re-read at any time, so it survives the install detour.
 *
 * Typing the code is a first-class fallback, not an afterthought: on iOS a denied camera permission
 * inside a PWA is buried in Settings and effectively unrecoverable in-app, and the codes are minted
 * from an alphabet chosen to be transcribable.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import type QrScanner from 'qr-scanner';
import { parseInviteCode } from '../utils/inviteCode';

interface InviteScannerDialogProps {
  open: boolean;
  onClose: () => void;
  /** Called with a validated, canonical invite code. */
  onCode: (code: string) => void;
}

export function InviteScannerDialog({ open, onClose, onCode }: InviteScannerDialogProps) {
  const { t } = useTranslation();
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));

  const videoRef = useRef<HTMLVideoElement | null>(null);
  // The scan callback fires per frame; once we have a code we must ignore the rest, or a QR
  // held in front of the camera resolves the same invite several times over.
  const handledRef = useRef(false);

  const [starting, setStarting] = useState(true);
  const [cameraFailed, setCameraFailed] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [manualError, setManualError] = useState<string | null>(null);

  const handleCode = useCallback((raw: string) => {
    if (handledRef.current) return;
    const code = parseInviteCode(raw);
    if (!code) return; // Some other QR entirely — keep scanning rather than failing at the user.
    handledRef.current = true;
    onCode(code);
  }, [onCode]);

  // Reset scan state the moment the dialog opens (adjust-state-during-render).
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setStarting(true);
      setCameraFailed(false);
      setManualCode('');
      setManualError(null);
    }
  }

  useEffect(() => {
    if (!open) return;

    handledRef.current = false;

    let scanner: QrScanner | null = null;
    let cancelled = false;

    // Loaded on demand: the decoder and its worker are dead weight in the check-in bundle,
    // which is the first thing every participant downloads, often on poor rally wifi.
    void (async () => {
      try {
        const { default: QrScannerCtor } = await import('qr-scanner');
        if (cancelled || !videoRef.current) return;

        scanner = new QrScannerCtor(videoRef.current, (result) => handleCode(result.data), {
          preferredCamera: 'environment',
          highlightScanRegion: true,
          maxScansPerSecond: 5,
        });
        await scanner.start();
        if (cancelled) return;
        setStarting(false);
      } catch (err) {
        // Denied permission, no camera, or an insecure context. All of them mean the same thing
        // to the user: the camera is not an option here, so lead them to the code field instead.
        console.error('invite scanner error:', err);
        if (cancelled) return;
        setStarting(false);
        setCameraFailed(true);
      }
    })();

    return () => {
      cancelled = true;
      // Both calls matter: without them iOS leaves the camera indicator lit after the dialog closes.
      scanner?.stop();
      scanner?.destroy();
    };
  }, [open, handleCode]);

  const handleManualSubmit = () => {
    const code = parseInviteCode(manualCode);
    if (!code) {
      setManualError(t('components.inviteScanner.invalidCode'));
      return;
    }
    setManualError(null);
    handleCode(code);
  };

  return (
    <Dialog open={open} onClose={onClose} fullScreen={fullScreen} fullWidth maxWidth="xs">
      <DialogTitle>{t('components.inviteScanner.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          {cameraFailed ? (
            <Alert severity="warning">{t('components.inviteScanner.cameraError')}</Alert>
          ) : (
            <>
              <Typography variant="body2" color="text.secondary">
                {t('components.inviteScanner.hint')}
              </Typography>
              <Box
                sx={{
                  position: 'relative',
                  width: '100%',
                  aspectRatio: '1 / 1',
                  borderRadius: 1,
                  overflow: 'hidden',
                  bgcolor: 'common.black',
                }}
              >
                <Box
                  component="video"
                  ref={videoRef}
                  sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
                {starting && (
                  <Box
                    sx={{
                      position: 'absolute',
                      inset: 0,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <CircularProgress />
                  </Box>
                )}
              </Box>
            </>
          )}

          <Divider>
            <Typography variant="caption" color="text.secondary">
              {t('components.inviteScanner.manualDivider')}
            </Typography>
          </Divider>

          <Typography variant="body2" color="text.secondary">
            {t('components.inviteScanner.manualHint')}
          </Typography>
          <TextField
            label={t('components.inviteScanner.manualLabel')}
            value={manualCode}
            onChange={(e) => setManualCode(e.target.value.toUpperCase())}
            error={Boolean(manualError)}
            helperText={manualError}
            slotProps={{ htmlInput: { autoCapitalize: 'characters', autoCorrect: 'off', spellCheck: false } }}
            fullWidth
            autoFocus={cameraFailed}
          />
          <Button variant="contained" size="large" fullWidth onClick={handleManualSubmit}>
            {t('components.inviteScanner.join')}
          </Button>
          <Button onClick={onClose} fullWidth>
            {t('common.cancel')}
          </Button>
        </Stack>
      </DialogContent>
    </Dialog>
  );
}

export default InviteScannerDialog;
