/**
 * Main App Component with Routing
 */
import type {ReactNode} from 'react';
import {lazy, Suspense, useCallback, useEffect, useState} from 'react';
import {BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate} from 'react-router-dom';
import {Alert, Box, Button, CircularProgress, Container, createTheme, CssBaseline, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Paper, Snackbar, Stack, ThemeProvider, Typography} from '@mui/material';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import {TeamLayout} from './components/TeamLayout';
import {AdminLayout} from './components/AdminLayout';
import {storage} from './services/storage';
import {config} from './config';
import {seedMockAdminSession, seedMockTeamSession} from './services/mockSession';
import {useGameStore} from './store/useGameStore';
import {useTeamStore} from './store/useTeamStore';
import {canAccessAdminPath, getRolesFromToken, getUserRoleFromRoles} from './services/auth';
import {
  getNotificationPermission,
  initializePushNotifications,
  onForegroundMessage,
  registerIfPermissionGranted
} from './services/firebaseMessaging';
import {
  clearPendingMessageIds,
  getPendingMessageIds,
  type NotificationSyncMessage,
  removePendingMessageId,
  subscribeToNotifications
} from './services/notificationSync';
import {type Message, useMessageStore} from './store/useMessageStore';
import api from './services/api';

// Lazy-loaded participant pages
const CheckInPage = lazy(() => import('./pages/participant/Checkin'));
const DashboardPage = lazy(() => import('./pages/participant/Dashboard'));
const ParticipantInfoPage = lazy(() => import('./pages/participant/Info'));
const ParticipantMap = lazy(() => import('./pages/participant/Map'));
const RivalsPage = lazy(() => import('./pages/participant/Rivals'));
const RankingsPage = lazy(() => import('./pages/participant/Rankings'));
const PicturesPage = lazy(() => import('./pages/participant/Pictures'));

// Lazy-loaded admin pages
const AdminLogin = lazy(() => import('./pages/admin/AdminLogin'));
const AdminDashboard = lazy(() => import('./pages/admin/Dashboard'));
const AdminMap = lazy(() => import('./pages/admin/Map'));
const AdminMessages = lazy(() => import('./pages/admin/Messages'));
const AdminInbox = lazy(() => import('./pages/admin/Inbox'));
const Games = lazy(() => import('./pages/admin/Games'));
const Teams = lazy(() => import('./pages/admin/Teams'));
const Zones = lazy(() => import('./pages/admin/Zones'));
const Automations = lazy(() => import('./pages/admin/Automations'));
const Analytics = lazy(() => import('./pages/admin/Analytics'));
const Settings = lazy(() => import('./pages/admin/Settings'));
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'));
const CheckpointStation = lazy(() => import('./pages/admin/CheckpointStation'));
const PointsManagement = lazy(() => import('./pages/admin/PointsManagement'));
const AdminPictures = lazy(() => import('./pages/admin/Pictures'));

/**
 * Shared loading fallback for Suspense boundaries
 */
function PageLoader() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', mt: 8 }}>
      <CircularProgress />
    </Box>
  );
}

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#165656', // Vlettenrally groen/blauw
      light: '#2a7a7a',
      dark: '#0f3d3d',
      contrastText: '#fff',
    },
    secondary: {
      main: '#ffcb0f', // Vlettenrally geel accent
      light: '#ffd740',
      dark: '#c79f00',
      contrastText: '#000',
    },
    background: {
      default: '#f8f9fa',
      paper: '#ffffff',
    },
  },
  typography: {
    fontFamily: 'Roboto, sans-serif',
    fontSize: 14, // Smaller base size for mobile
    h4: {
      fontFamily: 'Poppins, sans-serif', // Speelser voor titels
      fontSize: '1.5rem',
      fontWeight: 600,
      color: '#165656',
    },
    h5: {
      fontFamily: 'Poppins, sans-serif',
      fontSize: '1.25rem',
      fontWeight: 600, // Poppins heeft mooiere 600
      color: '#165656',
    },
    h6: {
      fontFamily: 'Poppins, sans-serif',
      fontSize: '1.1rem',
      fontWeight: 600,
    },
    body1: {
      fontSize: '0.95rem',
    },
    body2: {
      fontSize: '0.875rem',
    },
    button: {
      fontFamily: 'Poppins, sans-serif', // Ook buttons speelser
      textTransform: 'none',
      fontWeight: 500,
      fontSize: '0.9rem',
    },
  },
  shape: {
    borderRadius: 4, // Veel kleinere radius
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 4,
          padding: '10px 24px',
          boxShadow: 'none',
          '&:hover': {
            boxShadow: '0 2px 8px rgba(22, 86, 86, 0.2)',
          },
        },
        contained: {
          '&:hover': {
            boxShadow: '0 4px 12px rgba(22, 86, 86, 0.3)',
          },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 6,
          boxShadow: '0 2px 12px rgba(0,0,0,0.08)',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          borderRadius: 6,
        },
      },
    },
  },
});

/**
 * In mock mode we show an explicit launcher instead of auth screens. Each button
 * seeds a fake local session (the app has no backend in mock mode) before opening
 * the interface in a new tab, so the route guards let the new tab through. Writes
 * to localStorage are synchronous and shared across same-origin tabs, so seeding
 * before window.open is enough for the new tab to read the session.
 */
function MockModeLauncher() {
  const openInterface = (seed: () => void, path: string) => () => {
    seed();
    window.open(path, '_blank', 'noopener,noreferrer');
  };

  return (
    <Container maxWidth="sm">
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', py: 3 }}>
        <Paper elevation={3} sx={{ p: 4, width: '100%' }}>
          <Stack spacing={2.5}>
            <Typography variant="h5" component="h1">
              Mock modus
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Kies welke interface je wilt openen. Beide openen in een nieuw tabblad.
            </Typography>
            <Button variant="contained" onClick={openInterface(seedMockAdminSession, '/admin/dashboard')}>
              Open admin interface
            </Button>
            <Button variant="outlined" onClick={openInterface(seedMockTeamSession, '/dashboard')}>
              Open player interface
            </Button>
          </Stack>
        </Paper>
      </Box>
    </Container>
  );
}

/**
 * Route guard for team/participant routes
 */
function TeamRoute({ children }: { children: ReactNode }) {
  const isCheckedIn = storage.isCheckedIn();

  if (!isCheckedIn) {
    return <Navigate to="/check-in" replace />;
  }

  return <>{children}</>;
}

/**
 * Rankings are reachable only once an admin has revealed the final standings at
 * the award ceremony. The menu item in TeamLayout is hidden in the same
 * condition; this guard prevents direct URL access before the reveal.
 */
function RankingsRoute({ children }: { children: ReactNode }) {
  const game = useGameStore((s) => s.game);
  if (game && !game.rankingsPublished) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

/**
 * Rivals are hidden once a team finishes or the game completes, so no scores
 * leak near the end. The menu item is hidden in the same condition; this guard
 * blocks direct URL access.
 */
function RivalsRoute({ children }: { children: ReactNode }) {
  const game = useGameStore((s) => s.game);
  const team = useTeamStore((s) => s.team);
  if (team?.finished || game?.status === 'completed') {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

/**
 * Route guard for admin routes
 */
function AdminRoute({ children }: { children: ReactNode }) {
  const token = storage.getAuthToken();
  const role = storage.getUserRole();
  const location = useLocation();

  // If no token, redirect to admin login
  if (!token) {
    return <Navigate to="/admin/login" replace />;
  }

  // Try to get role from token if not in storage
  if (!role) {
    const roles = getRolesFromToken(token);
    const tokenRole = getUserRoleFromRoles(roles);
    if (tokenRole) {
      storage.setUserRole(tokenRole);
      // Also store all roles
      if (roles.length > 0) {
        storage.setUserRoles(roles);
      }
      if (tokenRole !== 'admin') {
        return <Navigate to="/dashboard" replace />;
      }
    } else {
      return <Navigate to="/admin/login" replace />;
    }
  } else if (role !== 'admin') {
    return <Navigate to="/dashboard" replace />;
  }

  // Capability check: redirect staff to the dashboard if their role(s) can't access this page.
  // The dashboard itself has no role restriction, so this never loops.
  if (!canAccessAdminPath(token, location.pathname)) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  return <>{children}</>;
}

/**
 * Root redirect based on authentication state and role
 */
function RootRedirect() {
  const navigate = useNavigate();

  useEffect(() => {
    if (import.meta.env.DEV && config.useMocks) {
      navigate('/mock-entry', { replace: true });
      return;
    }

    const token = storage.getAuthToken();
    let role = storage.getUserRole();

    // Try to extract role from token if not stored
    if (token && !role) {
      const roles = getRolesFromToken(token);
      role = getUserRoleFromRoles(roles);
      if (role) {
        storage.setUserRole(role);
        // Also store all roles
        if (roles.length > 0) {
          storage.setUserRoles(roles);
        }
      }
    }

    if (role === 'admin') {
      navigate('/admin/dashboard', { replace: true });
    } else if (storage.isCheckedIn()) {
      navigate('/dashboard', { replace: true });
    } else {
      navigate('/check-in', { replace: true });
    }
  }, [navigate]);

  return null;
}

function App() {
  const [notification, setNotification] = useState<{ title: string; body: string } | null>(null);
  const [showNotificationPrompt, setShowNotificationPrompt] = useState(() => {
    // Initialize based on current permission state (only show if logged in and permission not yet requested)
    const token = storage.getAuthToken();
    if (!token) return false;
    return getNotificationPermission() === 'default';
  });

  const { addMessage, messages } = useMessageStore();

  // Fetch and add a single message to the store (avoiding duplicates)
  const fetchAndAddMessage = useCallback(async (messageId: string) => {
    // Check if message already exists in store
    if (messages.some((m) => m.id === messageId)) {
      return;
    }

    try {
      const response = await api.getMessage(messageId);
      if (response.data) {
        addMessage(response.data as Message);
        await removePendingMessageId(messageId);
      }
    } catch (error) {
      console.error('[App] Failed to fetch message:', messageId, error);
    }
  }, [addMessage, messages]);

  // Process pending messages from IndexedDB (queued while app was closed)
  const processPendingMessages = useCallback(async () => {
    try {
      const pendingIds = await getPendingMessageIds();
      if (pendingIds.length === 0) return;

      console.log('[App] Processing pending messages:', pendingIds.length);

      for (const messageId of pendingIds) {
        await fetchAndAddMessage(messageId);
      }

      await clearPendingMessageIds();
    } catch (error) {
      console.error('[App] Failed to process pending messages:', error);
    }
  }, [fetchAndAddMessage]);

  // Subscribe to BroadcastChannel for immediate notification sync
  useEffect(() => {
    return subscribeToNotifications((message: NotificationSyncMessage) => {
      console.log('[App] Received notification sync message:', message);

      if (message.type === 'NEW_MESSAGE' && message.messageId) {
        fetchAndAddMessage(message.messageId);
      } else if (message.type === 'MESSAGE_CLICKED' && message.messageId) {
        // Navigation will be handled by service worker postMessage
        fetchAndAddMessage(message.messageId);
      }
    });
  }, [fetchAndAddMessage]);

  // Process pending messages on app visibility change (app coming to foreground)
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!document.hidden) {
        processPendingMessages();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Also process on initial mount
    processPendingMessages();

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [processPendingMessages]);

  // Listen for postMessage from service worker for navigation
  useEffect(() => {
    const handleServiceWorkerMessage = (event: MessageEvent) => {
      if (event.data?.type === 'NAVIGATE' && event.data?.url) {
        // Navigate using window.location as we're outside BrowserRouter here
        // The actual navigation will be handled by the router
        window.location.href = event.data.url;
      }
    };

    navigator.serviceWorker?.addEventListener('message', handleServiceWorkerMessage);

    return () => {
      navigator.serviceWorker?.removeEventListener('message', handleServiceWorkerMessage);
    };
  }, []);

  // Initialize FCM for already logged-in users and set up foreground message handling
  useEffect(() => {
    const token = storage.getAuthToken();
    if (!token) return;

    const permission = getNotificationPermission();

    if (permission === 'granted') {
      // Permission already granted, just register the token
      registerIfPermissionGranted().catch((err) => {
        console.warn('Failed to register FCM token:', err);
      });
    }

    // Set up foreground message handler (async)
    let unsubscribe: (() => void) | null = null;

    onForegroundMessage((payload) => {
      // Show notification snackbar
      // Read title/body from data field since we're using data-only messages
      const title = payload.data?.title || payload.notification?.title || 'Vlettenrally';
      const body = payload.data?.body || payload.notification?.body || '';

      if (title || body) {
        setNotification({
          title: title,
          body: body,
        });
      }

      // Fetch and add the message to the store
      const messageId = payload.data?.messageId;
      if (messageId) {
        fetchAndAddMessage(messageId);
      }
    }).then((unsub) => {
      unsubscribe = unsub;
    });

    return () => {
      if (unsubscribe) unsubscribe();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Empty deps - only run once on mount

  const handleCloseNotification = useCallback(() => {
    setNotification(null);
  }, []);

  const handleEnableNotifications = useCallback(async () => {
    setShowNotificationPrompt(false);
    const success = await initializePushNotifications();
    if (!success) {
      console.warn('User declined notifications or initialization failed');
    }
  }, []);

  const handleDismissNotificationPrompt = useCallback(() => {
    setShowNotificationPrompt(false);
  }, []);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/mock-entry" element={import.meta.env.DEV && config.useMocks ? <MockModeLauncher /> : <Navigate to="/" replace />} />

          {/* Team/Participant routes */}
          <Route
            path="/check-in"
            element={import.meta.env.DEV && config.useMocks ? <MockModeLauncher /> : <Suspense fallback={<PageLoader />}><CheckInPage /></Suspense>}
          />
          <Route
            path="/dashboard"
            element={
              <TeamRoute>
                <TeamLayout>
                  <Suspense fallback={<PageLoader />}>
                    <DashboardPage />
                  </Suspense>
                </TeamLayout>
              </TeamRoute>
            }
          />
          <Route
            path="/info"
            element={
              <TeamRoute>
                <TeamLayout>
                  <Suspense fallback={<PageLoader />}>
                    <ParticipantInfoPage />
                  </Suspense>
                </TeamLayout>
              </TeamRoute>
            }
          />
          <Route
            path="/map"
            element={
              <TeamRoute>
                <TeamLayout>
                  <Suspense fallback={<PageLoader />}>
                    <ParticipantMap />
                  </Suspense>
                </TeamLayout>
              </TeamRoute>
            }
          />
          <Route
            path="/pictures"
            element={
              <TeamRoute>
                <TeamLayout>
                  <Suspense fallback={<PageLoader />}>
                    <PicturesPage />
                  </Suspense>
                </TeamLayout>
              </TeamRoute>
            }
          />
          <Route
            path="/rival"
            element={
              <TeamRoute>
                <RivalsRoute>
                  <TeamLayout>
                    <Suspense fallback={<PageLoader />}>
                      <RivalsPage />
                    </Suspense>
                  </TeamLayout>
                </RivalsRoute>
              </TeamRoute>
            }
          />
          <Route
            path="/rankings"
            element={
              <TeamRoute>
                <RankingsRoute>
                  <TeamLayout>
                    <Suspense fallback={<PageLoader />}>
                      <RankingsPage />
                    </Suspense>
                  </TeamLayout>
                </RankingsRoute>
              </TeamRoute>
            }
          />

          {/* Admin routes */}
          <Route
            path="/admin/login"
            element={import.meta.env.DEV && config.useMocks ? <MockModeLauncher /> : <Suspense fallback={<PageLoader />}><AdminLogin /></Suspense>}
          />
          <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
          <Route
            path="/admin/dashboard"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <AdminDashboard />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/map"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <AdminMap />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/games"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <Games />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/teams"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <Teams />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/points-management"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <PointsManagement />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/zones"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <Zones />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/messages"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <AdminMessages />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/inbox"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <AdminInbox />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/automations"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <Automations />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/analytics"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <Analytics />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/settings"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <Settings />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/admin-users"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <AdminUsers />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/checkpoint-station"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <CheckpointStation />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/pictures"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Suspense fallback={<PageLoader />}>
                    <AdminPictures />
                  </Suspense>
                </AdminLayout>
              </AdminRoute>
            }
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>

      {/* Foreground notification snackbar */}
      <Snackbar
        open={!!notification}
        autoHideDuration={6000}
        onClose={handleCloseNotification}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert onClose={handleCloseNotification} severity="info" sx={{ width: '100%' }}>
          <strong>{notification?.title}</strong>
          {notification?.body && <div>{notification.body}</div>}
        </Alert>
      </Snackbar>

      {/* Notification permission prompt */}
      <Dialog
        open={showNotificationPrompt}
        onClose={handleDismissNotificationPrompt}
        aria-labelledby="notification-dialog-title"
        aria-describedby="notification-dialog-description"
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle id="notification-dialog-title" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <NotificationsActiveIcon color="primary" />
          Meldingen inschakelen
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="notification-dialog-description">
            Wil je meldingen ontvangen van de organisatie? Je blijft dan op de hoogte van belangrijke updates tijdens de Vlettenrally.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ padding: 2, gap: 1 }}>
          <Button onClick={handleDismissNotificationPrompt} color="inherit">
            Later
          </Button>
          <Button onClick={handleEnableNotifications} variant="contained" color="primary">
            Inschakelen
          </Button>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}

export default App;
