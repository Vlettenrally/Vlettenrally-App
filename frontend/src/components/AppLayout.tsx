/**
 * App Layout Component - Contains navigation and layout structure
 */
import { useState } from 'react';
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
} from '@mui/icons-material';
import { useMessageStore } from '../store/useMessageStore';
import { storage } from '../services/storage';
import { useTeamStore } from '../store/useTeamStore';
import { useGameStore } from '../store/useGameStore';

interface AppLayoutProps {
  children: ReactNode;
}

export function AppLayout({ children }: AppLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { unreadCount } = useMessageStore();
  const { reset: resetTeam } = useTeamStore();
  const { reset: resetGame } = useGameStore();

  const menuItems = [
    { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard' },
    { text: 'Berichten', icon: <MessageIcon />, path: '/messages', badge: unreadCount },
    { text: 'Rankings', icon: <LeaderboardIcon />, path: '/rankings' },
    { text: 'Rivaal', icon: <RivalIcon />, path: '/rival' },
    { text: 'Prestaties', icon: <TrophyIcon />, path: '/achievements' },
  ];

  const handleNavigate = (path: string) => {
    navigate(path);
    setDrawerOpen(false);
  };

  const handleLogout = () => {
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
