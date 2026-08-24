import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  Paper,
  TablePagination,
  Typography,
} from '@mui/material';
import MarkEmailReadIcon from '@mui/icons-material/MarkEmailRead';
import api from '../../services/api';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
import websocket, { WebSocketEventType } from '../../services/websocket';
import type { AdminInboxNotification } from '../../types/entities';

interface AutomationNotificationPayload {
  notificationId?: string;
  teamId?: string;
  teamName?: string;
  source?: string;
  text?: string;
  createdAt?: string;
}

export default function AdminInbox() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);
  const [notifications, setNotifications] = useState<AdminInboxNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [markingAll, setMarkingAll] = useState(false);

  const unreadCount = useMemo(() => notifications.filter((n) => !n.read).length, [notifications]);

  const loadNotifications = useCallback(async () => {
    if (!selectedGameId) return;
    setLoading(true);
    setError(null);
    const result = await api.getAdminInboxByGame(selectedGameId, page, pageSize);
    setLoading(false);
    if (result.error) {
      setError(result.error);
      return;
    }
    if (result.data) {
      setNotifications(result.data.notifications);
      setTotalCount(result.data.totalCount);
    }
  }, [selectedGameId, page, pageSize]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadNotifications();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadNotifications]);

  useAdminResync(() => void loadNotifications());

  // Only receive automation notifications from the backend while this page is mounted.
  useAdminTopicHeartbeat(ADMIN_TOPICS.INBOX);

  useEffect(() => {
    const unsubAutomation = websocket.on(WebSocketEventType.AUTOMATION_NOTIFICATION, (message) => {
      const payload = message.payload as AutomationNotificationPayload;
      if (!payload.notificationId || !payload.text) return;
      const notificationId = payload.notificationId;
      const text = payload.text;
      setNotifications((current) => {
        if (current.some((n) => n.id === notificationId)) {
          return current;
        }
        const next: AdminInboxNotification = {
          id: notificationId,
          gameId: selectedGameId || '',
          teamId: payload.teamId,
          teamName: payload.teamName,
          source: payload.source || WebSocketEventType.AUTOMATION_NOTIFICATION,
          content: text,
          createdAt: payload.createdAt || new Date().toISOString(),
          read: false,
        };
        return [next, ...current];
      });
      setTotalCount((value) => value + 1);
    });

    return () => {
      unsubAutomation();
    };
  }, [selectedGameId]);

  const markAsRead = async (notificationId: string) => {
    setNotifications((current) =>
      current.map((n) =>
        n.id === notificationId ? { ...n, read: true, readAt: new Date().toISOString() } : n
      )
    );
    await api.markAdminInboxNotificationAsRead(notificationId);
  };

  const markAllAsRead = async () => {
    if (!selectedGameId) return;
    setMarkingAll(true);
    await api.markAllAdminInboxNotificationsAsRead(selectedGameId);
    setMarkingAll(false);
    setNotifications((current) =>
      current.map((n) => ({ ...n, read: true, readAt: n.readAt || new Date().toISOString() }))
    );
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          {t('admin.inbox.title')}
        </Typography>
        <Button
          variant="outlined"
          startIcon={markingAll ? <CircularProgress size={18} /> : <MarkEmailReadIcon />}
          disabled={!selectedGameId || unreadCount === 0 || markingAll}
          onClick={markAllAsRead}
        >
          {t('admin.inbox.markAllRead', { count: unreadCount })}
        </Button>
      </Box>

      {!selectedGameId && <Alert severity="info">{t('admin.inbox.selectGame')}</Alert>}

      {selectedGameId && (
        <>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Paper>
            {loading ? (
              <Box sx={{ py: 5, display: 'flex', justifyContent: 'center' }}>
                <CircularProgress />
              </Box>
            ) : notifications.length === 0 ? (
              <Box sx={{ py: 5, textAlign: 'center' }}>
                <Typography color="textSecondary">{t('admin.inbox.noNotifications')}</Typography>
              </Box>
            ) : (
              <List disablePadding>
                {notifications.map((notification, index) => (
                  <ListItem
                    key={notification.id}
                    divider={index < notifications.length - 1}
                    secondaryAction={
                      !notification.read ? (
                        <Button size="small" onClick={() => void markAsRead(notification.id)}>
                          {t('admin.inbox.markRead')}
                        </Button>
                      ) : undefined
                    }
                    sx={{ bgcolor: notification.read ? 'inherit' : 'action.hover' }}
                  >
                    <ListItemText
                      slotProps={{
                        primary: { component: 'div' },
                        secondary: { component: 'div' },
                      }}
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, pr: 10 }}>
                          <Typography variant="body1">{notification.content}</Typography>
                          {!notification.read && (
                            <Chip size="small" color="primary" label={t('admin.inbox.new')} />
                          )}
                        </Box>
                      }
                      secondary={
                        <Box sx={{ display: 'flex', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                          {notification.teamName && (
                            <Typography variant="caption" color="textSecondary">
                              {t('common.team')}: {notification.teamName}
                            </Typography>
                          )}
                          <Typography variant="caption" color="textSecondary">
                            {new Date(notification.createdAt).toLocaleString(undefined)}
                          </Typography>
                          <Chip size="small" variant="outlined" label={notification.source} />
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            )}
            <TablePagination
              component="div"
              count={totalCount}
              page={page}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(event) => {
                setPageSize(parseInt(event.target.value, 10));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 20, 50]}
              labelRowsPerPage={t('admin.inbox.rowsPerPage')}
            />
          </Paper>
        </>
      )}
    </Box>
  );
}
