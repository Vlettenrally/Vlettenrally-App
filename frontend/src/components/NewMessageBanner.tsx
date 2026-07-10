/**
 * NewMessageBanner
 *
 * Shows a clear, persistent banner at the top of the team app whenever a new
 * message arrives while the app is open. Foreground push notifications are
 * commonly suppressed by mobile browsers, so this gives players a visible cue
 * on any team page (not just the Dashboard feed).
 *
 * It reacts to `latestArrival` from the message store, which is bumped only on
 * genuine live arrivals (WebSocket / push), never on bulk page-load — so a
 * refresh with existing unread messages does not trigger a banner.
 *
 * The banner is persistent (no auto-hide): it stays until the player taps it
 * (→ open the Dashboard feed, mark read) or closes it (✕).
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Chip, Collapse, IconButton, Typography } from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useMessageStore, type Message } from '../store/useMessageStore';
import { getMessageIcon } from '../utils/messageAppearance';

// Only banner arrivals whose sentAt is within this window. Guards against stale
// messages replayed through addMessage by background-sync when the app reopens.
const RECENT_WINDOW_MS = 2 * 60 * 1000;

export function NewMessageBanner() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const latestArrival = useMessageStore((s) => s.latestArrival);
  const markAsRead = useMessageStore((s) => s.markAsRead);

  // Queue of undismissed arrivals; the head is displayed.
  const [queue, setQueue] = useState<Message[]>([]);

  useEffect(() => {
    if (!latestArrival) return;
    const { message } = latestArrival;
    if (message.read) return;
    const age = Date.now() - new Date(message.sentAt).getTime();
    if (Number.isNaN(age) || age > RECENT_WINDOW_MS) return;

    setQueue((prev) => {
      if (prev.some((m) => m.id === message.id)) return prev;
      return [...prev, message];
    });
    // Key on nonce so repeated arrivals each fire, even if otherwise equal.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [latestArrival?.nonce]);

  const head = queue[0];
  const extra = queue.length - 1;

  const handleView = () => {
    if (head) markAsRead(head.id);
    setQueue([]);
    navigate('/dashboard');
  };

  const handleClose = (e: React.MouseEvent) => {
    // Don't let the close click bubble to the Alert's onClick (which navigates).
    e.stopPropagation();
    // Dismiss all pending banners; they remain unread in the Dashboard feed.
    setQueue([]);
  };

  const time = head
    ? new Date(head.sentAt).toLocaleTimeString('nl-NL', { hour: '2-digit', minute: '2-digit' })
    : '';

  return (
    <Collapse in={Boolean(head)} unmountOnExit>
      {head && (
        <Alert
          severity="info"
          icon={getMessageIcon(head.messageType)}
          action={
            <IconButton
              size="small"
              aria-label={t('components.newMessageBanner.close')}
              onClick={handleClose}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          }
          onClick={handleView}
          sx={{
            borderRadius: 0,
            cursor: 'pointer',
            alignItems: 'flex-start',
            '& .MuiAlert-message': { width: '100%' },
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              {t('components.newMessageBanner.title')}
            </Typography>
            {extra > 0 && (
              <Chip
                label={t('components.newMessageBanner.more', { count: extra })}
                color="primary"
                size="small"
                sx={{ height: 20, fontSize: '0.7rem' }}
              />
            )}
            <Typography variant="caption" sx={{ ml: 'auto', color: 'text.secondary' }}>
              {time}
            </Typography>
          </Box>
          <Typography variant="body2" sx={{ mt: 0.5 }}>
            {head.content}
          </Typography>
          <Typography variant="caption" sx={{ mt: 0.5, display: 'block', fontWeight: 600, color: 'primary.main' }}>
            {t('components.newMessageBanner.view')}
          </Typography>
        </Alert>
      )}
    </Collapse>
  );
}

export default NewMessageBanner;
