/**
 * Team Layout Component - Contains navigation and layout structure for team interface
 */
import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
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
  Badge,
  Divider,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Dashboard as DashboardIcon,
  Message as MessageIcon,
  EmojiEvents as TrophyIcon,
  CompareArrows as RivalIcon,
  Leaderboard as LeaderboardIcon,
  Logout as LogoutIcon,
  WifiOff as WifiOffIcon,
  Map as MapIcon,
  Description as DescriptionIcon,
} from '@mui/icons-material';
import { useMessageStore } from '../store/useMessageStore';
import { storage } from '../services/storage';
import { useTeamStore } from '../store/useTeamStore';
import { useGameStore } from '../store/useGameStore';
import { useConnectionStatus } from '../hooks/useConnectionStatus';
import { useTeamRealtime } from '../hooks/useRealtime';
import api from '../services/api';

interface TeamLayoutProps {
  children: ReactNode;
}

export function TeamLayout({ children }: TeamLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { unreadCount } = useMessageStore();
  const { reset: resetTeam, team, setTeam } = useTeamStore();
  const { reset: resetGame, game, fetchGameById } = useGameStore();
  const { showDisconnected } = useConnectionStatus();
  const gameId = storage.getGameId();
  const teamId = storage.getTeamId();

  // Initialize team data from storage if not in store (e.g., after page refresh)
  useEffect(() => {
    if (!team && teamId) {
      // First try to load from storage cache
      const cachedTeam = storage.getTeamData();
      if (cachedTeam) {
        setTeam(cachedTeam);
      }
      // Then fetch fresh data from API
      api.getMyTeam().then(response => {
        if (response.data) {
          setTeam(response.data);
          storage.setTeamData(response.data);
        }
      }).catch(err => {
        console.warn('Failed to fetch team data:', err);
      });
    }
  }, [team, teamId, setTeam]);

  // Initialize websocket connection for the entire team app
  // Use teamId from storage as fallback when store is not yet populated
  useTeamRealtime({ gameId, teamId: team?.id || teamId || null });

  // Load the game so we can gate the Rankings menu/route on its status.
  useEffect(() => {
    if (!game && gameId) {
      fetchGameById(gameId).catch(() => undefined);
    }
  }, [game, gameId, fetchGameById]);

  const gameCompleted = game?.status === 'completed';
  const menuItems = [
    { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard' },
    ...(game?.participantInfo
      ? [{ text: 'Spelinformatie', icon: <DescriptionIcon />, path: '/info' }]
      : []),
    { text: 'Kaart', icon: <MapIcon />, path: '/map' },
    { text: 'Berichten', icon: <MessageIcon />, path: '/messages', badge: unreadCount },
    ...(gameCompleted
      ? [{ text: 'Eindstand', icon: <LeaderboardIcon />, path: '/rankings' }]
      : []),
    { text: 'Rivaal', icon: <RivalIcon />, path: '/rival' },
    { text: 'Prestaties', icon: <TrophyIcon />, path: '/achievements' },
  ];

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
    resetTeam();
    resetGame();
    navigate('/check-in');
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="sticky">
        <Toolbar>
          <IconButton
            size="large"
            edge="start"
            color="inherit"
            aria-label="menu"
            sx={{ mr: 2 }}
            onClick={() => setDrawerOpen(true)}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Vlettenrally
          </Typography>
          <IconButton color="inherit" onClick={() => navigate('/messages')}>
            <Badge badgeContent={unreadCount} color="error">
              <MessageIcon />
            </Badge>
          </IconButton>
        </Toolbar>
      </AppBar>

      {showDisconnected && (
        <Alert 
          severity="warning" 
          icon={<WifiOffIcon />}
          sx={{ borderRadius: 0 }}
        >
          Geen verbinding met server. Sommige gegevens kunnen verouderd zijn.
        </Alert>
      )}

      <Drawer anchor="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 250 }} role="presentation">
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" color="primary">
              Vlettenrally
            </Typography>
          </Box>
          <Divider />
          <List>
            {menuItems.map((item) => (
              <ListItem key={item.path} disablePadding>
                <ListItemButton
                  selected={location.pathname === item.path}
                  onClick={() => handleNavigate(item.path)}
                >
                  <ListItemIcon>
                    {item.badge ? (
                      <Badge badgeContent={item.badge} color="error">
                        {item.icon}
                      </Badge>
                    ) : (
                      item.icon
                    )}
                  </ListItemIcon>
                  <ListItemText primary={item.text} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
          <Divider />
          <List>
            <ListItem disablePadding>
              <ListItemButton onClick={handleLogout}>
                <ListItemIcon>
                  <LogoutIcon />
                </ListItemIcon>
                <ListItemText primary="Uitloggen" />
              </ListItemButton>
            </ListItem>
          </List>
        </Box>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, bgcolor: 'background.default' }}>
        {children}
      </Box>
    </Box>
  );
}
