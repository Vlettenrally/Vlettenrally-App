/**
 * Admin Layout Component - Contains navigation and layout structure for admin interface
 * Features responsive sidebar: drawer on mobile, persistent collapsible sidebar on desktop
 */
import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  AppBar,
  Box,
  Toolbar,
  Typography,
  IconButton,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  Divider,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  useMediaQuery,
  useTheme,
  Tooltip,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import {
  Menu as MenuIcon,
  SportsScore as GamesIcon,
  Groups as TeamsIcon,
  HowToReg as HowToRegIcon,
  Message as MessageIcon,
  Inbox as InboxIcon,
  AutoFixHigh as AutomationsIcon,
  Logout as LogoutIcon,
  AdminPanelSettings as AdminIcon,
  ChevronLeft as CollapseIcon,
  ChevronRight as ExpandIcon,
  Map as MapIcon,
  WifiOff as WifiOffIcon,
  Settings as SettingsIcon,
  ManageAccounts as ManageAccountsIcon,
  Place as PlaceIcon,
  Flag as FlagIcon,
  Scoreboard as ScoreboardIcon,
  EmojiEvents as RankingIcon,
  PhotoLibrary as PhotoLibraryIcon,
} from '@mui/icons-material';
import { storage } from '../services/storage';
import { useGameStore } from '../store/useGameStore';
import { useConnectionStatus } from '../hooks/useConnectionStatus';
import { useAdminWebSocket } from '../hooks/useWebSocket';
import { useAdminSync, useAdminResync } from '../hooks/useAdminSync';
import { canAccessAdminPath } from '../services/auth';
import api from '../services/api';
import {
  ActiveGamesWidget,
  CheckInProgressWidget,
  GameClockWidget,
} from './widgets';

const SIDEBAR_WIDTH = 280;
const SIDEBAR_COLLAPSED_WIDTH = 64;

interface AdminLayoutProps {
  children: ReactNode;
}

export function AdminLayout({ children }: AdminLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => storage.getSidebarCollapsed());

  const token = storage.getAuthToken();

  // Use game store instead of local state
  const { games, selectedGameId, setSelectedGameId, fetchGames } = useGameStore();
  const { showDisconnected } = useConnectionStatus();

  // Load games on mount
  useEffect(() => {
    fetchGames();
  }, [fetchGames]);

  // Auto-select first game if none selected
  useEffect(() => {
    if (!selectedGameId && games.length > 0) {
      setSelectedGameId(games[0].id);
    }
  }, [games, selectedGameId, setSelectedGameId]);

  // Admin websocket connection - hook manages connect/disconnect lifecycle
  useAdminWebSocket(selectedGameId);

  // Drive the admin resync signal (reconnect / app resume) for the whole shell,
  // and refresh the games list when it fires.
  useAdminSync();
  useAdminResync(fetchGames);

  // Persist sidebar collapsed state
  useEffect(() => {
    storage.setSidebarCollapsed(sidebarCollapsed);
  }, [sidebarCollapsed]);

  const allMenuItems = [
    { text: t('admin.layout.map'), icon: <MapIcon />, path: '/admin/map' },
    { text: t('admin.layout.games'), icon: <GamesIcon />, path: '/admin/games' },
    { text: t('admin.layout.teams'), icon: <TeamsIcon />, path: '/admin/teams' },
    { text: t('admin.layout.checkIn'), icon: <HowToRegIcon />, path: '/admin/check-in' },
    { text: t('admin.layout.pointsManagement'), icon: <ScoreboardIcon />, path: '/admin/points-management' },
    { text: t('admin.layout.ranking'), icon: <RankingIcon />, path: '/admin/ranking' },
    { text: t('admin.layout.zones'), icon: <PlaceIcon />, path: '/admin/zones' },
    { text: t('admin.layout.adminUsers'), icon: <ManageAccountsIcon />, path: '/admin/admin-users' },
    { text: t('admin.layout.checkpointStation'), icon: <FlagIcon />, path: '/admin/checkpoint-station' },
    { text: t('admin.layout.messages'), icon: <MessageIcon />, path: '/admin/messages' },
    { text: t('admin.layout.inbox'), icon: <InboxIcon />, path: '/admin/inbox' },
    { text: t('admin.layout.automations'), icon: <AutomationsIcon />, path: '/admin/automations' },
    { text: t('admin.layout.pictures'), icon: <PhotoLibraryIcon />, path: '/admin/pictures' },
    { text: t('admin.layout.settings'), icon: <SettingsIcon />, path: '/admin/settings' },
  ];

  // Only show menu items the current user's role(s) are allowed to access.
  const menuItems = token
    ? allMenuItems.filter((item) => canAccessAdminPath(token, item.path))
    : allMenuItems;

  const handleNavigate = (path: string) => {
    navigate(path);
    setDrawerOpen(false);
  };

  const handleLogout = async () => {
    // Call logout endpoint to revoke tokens (silent failure if request fails)
    try {
      await api.logout();
    } catch (error) {
      console.warn('Logout API call failed:', error);
    }

    // Clear cached app data and session
    storage.clearCache();
    storage.clearSession();
    navigate('/admin/login');
  };

  const handleGameChange = (event: SelectChangeEvent) => {
    const gameId = event.target.value;
    setSelectedGameId(gameId);
  };

  const sidebarWidth = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH;

  // Sidebar content - shared between desktop sidebar and mobile drawer
  const sidebarContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box sx={{ p: 2, bgcolor: '#1a237e', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {!sidebarCollapsed && (
          <Typography variant="h6">{t('admin.layout.adminPanel')}</Typography>
        )}
        {isDesktop && (
          <IconButton
            size="small"
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            sx={{ color: 'white', ml: sidebarCollapsed ? 'auto' : 0, mr: sidebarCollapsed ? 'auto' : 0 }}
          >
            {sidebarCollapsed ? <ExpandIcon /> : <CollapseIcon />}
          </IconButton>
        )}
      </Box>
      <List>
        {menuItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <Tooltip title={sidebarCollapsed ? item.text : ''} placement="right">
              <ListItemButton
                selected={location.pathname === item.path}
                onClick={() => handleNavigate(item.path)}
                sx={{ justifyContent: sidebarCollapsed ? 'center' : 'flex-start', px: sidebarCollapsed ? 1 : 2 }}
              >
                <ListItemIcon sx={{ minWidth: sidebarCollapsed ? 0 : 40, justifyContent: 'center' }}>
                  {item.icon}
                </ListItemIcon>
                {!sidebarCollapsed && <ListItemText primary={item.text} />}
              </ListItemButton>
            </Tooltip>
          </ListItem>
        ))}
      </List>
      <Divider />

      {/* Widgets section - only shown on desktop when not collapsed */}
      {isDesktop && !sidebarCollapsed && (
        <Box sx={{ p: 2, flexGrow: 1, overflowY: 'auto' }}>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
            {t('admin.layout.quickStats')}
          </Typography>
          <GameClockWidget collapsed={sidebarCollapsed} />
          <ActiveGamesWidget collapsed={sidebarCollapsed} />
          <CheckInProgressWidget collapsed={sidebarCollapsed} />
        </Box>
      )}

      {/* Collapsed widgets - icon only */}
      {isDesktop && sidebarCollapsed && (
        <Box sx={{ py: 2, flexGrow: 1 }}>
          <GameClockWidget collapsed />
          <ActiveGamesWidget collapsed />
          <CheckInProgressWidget collapsed />
        </Box>
      )}

      <Box sx={{ mt: 'auto' }}>
        <Divider />
        <List>
          <ListItem disablePadding>
            <Tooltip title={sidebarCollapsed ? t('admin.layout.logout') : ''} placement="right">
              <ListItemButton
                onClick={handleLogout}
                sx={{ justifyContent: sidebarCollapsed ? 'center' : 'flex-start', px: sidebarCollapsed ? 1 : 2 }}
              >
                <ListItemIcon sx={{ minWidth: sidebarCollapsed ? 0 : 40, justifyContent: 'center' }}>
                  <LogoutIcon />
                </ListItemIcon>
                {!sidebarCollapsed && <ListItemText primary={t('admin.layout.logout')} />}
              </ListItemButton>
            </Tooltip>
          </ListItem>
        </List>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Desktop persistent sidebar */}
      {isDesktop && (
        <Box
          component="nav"
          sx={{
            width: sidebarWidth,
            flexShrink: 0,
            transition: theme.transitions.create('width', {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.enteringScreen,
            }),
          }}
        >
          <Box
            sx={{
              width: sidebarWidth,
              height: '100vh',
              position: 'fixed',
              borderRight: 1,
              borderColor: 'divider',
              bgcolor: 'background.paper',
              transition: theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.enteringScreen,
              }),
              overflowX: 'hidden',
            }}
          >
            {sidebarContent}
          </Box>
        </Box>
      )}

      {/* Mobile drawer */}
      {!isDesktop && (
        <Drawer anchor="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
          <Box sx={{ width: 250 }} role="presentation">
            {sidebarContent}
          </Box>
        </Drawer>
      )}

      {/* Main content area */}
      <Box sx={{ display: 'flex', flexDirection: 'column', flexGrow: 1, minWidth: 0 }}>
        <AppBar position="sticky" sx={{ bgcolor: '#1a237e' }}>
          <Toolbar>
            {/* Only show hamburger on mobile */}
            {!isDesktop && (
              <IconButton
                size="large"
                edge="start"
                color="inherit"
                aria-label={t('components.teamLayout.menuAriaLabel')}
                sx={{ mr: 2 }}
                onClick={() => setDrawerOpen(true)}
              >
                <MenuIcon />
              </IconButton>
            )}
            <AdminIcon sx={{ mr: 1 }} />
            <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
              {t('admin.layout.title')}
            </Typography>
            {games.length > 0 && (
              <FormControl size="small" sx={{ minWidth: 150, mr: 2 }}>
                <InputLabel id="game-select-label" sx={{ color: 'white' }}>{t('admin.layout.game')}</InputLabel>
                <Select
                  labelId="game-select-label"
                  value={selectedGameId || ''}
                  label={t('admin.layout.game')}
                  onChange={handleGameChange}
                  sx={{
                    color: 'white',
                    '.MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.5)' },
                    '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'white' },
                    '.MuiSvgIcon-root': { color: 'white' },
                  }}
                >
                  {games.map((game) => (
                    <MenuItem key={game.id} value={game.id}>
                      {game.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}
          </Toolbar>
        </AppBar>

        {showDisconnected && (
          <Alert
            severity="warning"
            icon={<WifiOffIcon />}
            sx={{ borderRadius: 0 }}
          >
            {t('admin.layout.noConnection')}
          </Alert>
        )}

        <Box component="main" sx={{ flexGrow: 1, bgcolor: 'background.default', p: 0 }}>
          {children}
        </Box>
      </Box>
    </Box>
  );
}
