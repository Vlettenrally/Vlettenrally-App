/**
 * Team Layout Component - Contains navigation and layout structure for team interface
 */
import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
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
} from '@mui/material';
import {
  Menu as MenuIcon,
  Dashboard as DashboardIcon,
  CompareArrows as RivalIcon,
  Leaderboard as LeaderboardIcon,
  Logout as LogoutIcon,
  Map as MapIcon,
  Description as DescriptionIcon,
} from '@mui/icons-material';
import { storage } from '../services/storage';
import { useTeamStore } from '../store/useTeamStore';
import { useGameStore } from '../store/useGameStore';
import { useTeamRealtime } from '../hooks/useRealtime';
import { useTeamSync } from '../hooks/useTeamSync';
import api from '../services/api';
import { mockApi } from '../services/mockApi';
import { config } from '../config';

interface TeamLayoutProps {
  children: ReactNode;
}

export function TeamLayout({ children }: TeamLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { reset: resetTeam, team, setTeam } = useTeamStore();
  const { reset: resetGame, game, fetchGameById } = useGameStore();
  const gameId = storage.getGameId();
  const teamId = storage.getTeamId();

  // Initialize team data from storage if not in store (e.g., after page refresh)
  useEffect(() => {
    if (!team && teamId) {
      // Use mock API in mock mode
      const apiToUse = config.useMocks ? mockApi : api;
      
      // First try to load from storage cache
      const cachedTeam = storage.getTeamData();
      if (cachedTeam) {
        setTeam(cachedTeam);
      }
      // Then fetch fresh data from API
      apiToUse.getMyTeam().then(response => {
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

  // Re-sync full player state on reconnect / app resume so data that changed
  // while offline or suspended is refreshed without needing a remount.
  useTeamSync({ gameId, teamId: team?.id || teamId || null });

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
    ...(gameCompleted
      ? [{ text: 'Eindstand', icon: <LeaderboardIcon />, path: '/rankings' }]
      : []),
    { text: 'Rivaal', icon: <RivalIcon />, path: '/rival' },
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
          
          {team ? (
            <Box sx={{ flexGrow: 1 }}>
              <Typography 
                variant="h6" 
                component="div" 
                sx={{ 
                  fontFamily: 'Poppins, Roboto, sans-serif',
                  fontWeight: 600,
                  fontSize: '1.1rem',
                  lineHeight: 1.2,
                }}
              >
                {team.description}
              </Typography>
              <Typography 
                variant="caption" 
                component="div" 
                sx={{ 
                  fontSize: '0.75rem',
                  opacity: 0.9,
                  lineHeight: 1,
                }}
              >
                {team.name}
              </Typography>
            </Box>
          ) : (
            <Typography 
              variant="h5" 
              component="div" 
              sx={{ 
                flexGrow: 1,
                fontFamily: 'Poppins, Roboto, sans-serif',
                fontWeight: 600,
                fontSize: '1.4rem',
                color: 'inherit',
              }}
            >
              Vlettenrally
            </Typography>
          )}
        </Toolbar>
      </AppBar>

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
                  <ListItemIcon>{item.icon}</ListItemIcon>
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
